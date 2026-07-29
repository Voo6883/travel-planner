'use client';

import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { ApiError, i18nKeyForErrorCode } from '@/lib/api/api-error';
import { chatMessagesPath, fetchChatHistory, type ChatTarget } from '@/lib/api/chat-api';
import { streamChatMessage } from '@/lib/api/chat-stream';
import type { ChatFrame, ChatStreamErrorPayload } from '@/lib/api/chat-events';
import {
  chatReducer,
  initialChatState,
  isStreaming as turnInFlight,
  type ChatMessage,
  type ChatState,
} from '../lib/chat-state';
import { newClientMessageId } from '../lib/client-message-id';

/**
 * The chat transport hook (PLAN §2602, ADR 007).
 *
 * It owns the three things a pure reducer cannot: the request, the `AbortController`, and the
 * reconnect timer. Everything it learns is handed to `chatReducer` as an action, so the awkward
 * sequences — a drop half-way through a sentence, a resume that replays frames, a retry of a
 * message the server already committed — are reproducible in a unit test without a network.
 *
 * ## Why not React Query
 *
 * The rest of the app reads server state through React Query, and chat deliberately does not. A
 * conversation is an append-only event log, not a cacheable resource: there is no coherent "stale"
 * moment for a half-streamed sentence, and a background refetch that replaced the message list
 * mid-turn would rewind text a user is reading. History still arrives over `apiRequest`, but the
 * reducer is the single owner of what is on screen.
 *
 * ## Reconnect
 *
 * A dropped body is not a finished turn. ADR 007 says a terminal failure is always an
 * `event: error` frame, so a stream that simply *ends* without `done` or `error` was cut — the
 * hook marks the turn interrupted (§7.4), then re-POSTs with `Last-Event-ID` so the server can
 * replay from where the client stopped. The same client message id goes out again, which is what
 * makes the retry safe: the server recognises the message it already committed.
 */

export interface UseChatStreamOptions {
  readonly target: ChatTarget;
  /** Existing conversation to continue. The first planner turn creates one, so it starts null. */
  readonly conversationId?: string | null;
  /** Load page 0 of history on mount. Off for a brand-new planner session with nothing to load. */
  readonly loadHistoryOnMount?: boolean;
  readonly maxReconnectAttempts?: number;
  readonly reconnectDelayMs?: number;
}

export interface ChatStreamController {
  readonly state: ChatState;
  readonly messages: readonly ChatMessage[];
  readonly isStreaming: boolean;
  readonly error: ChatStreamErrorPayload | null;
  readonly hasMoreHistory: boolean;
  readonly isLoadingHistory: boolean;
  /** Sends a new user message. Ignored while a turn is in flight — §7.3 shows Stop, not Send. */
  readonly send: (text: string) => void;
  /** Re-sends the last message on its original client id. Never creates a second bubble. */
  readonly retry: () => void;
  /** §7.3: stop generation without losing what already arrived. */
  readonly stop: () => void;
  /** Pages backwards through the conversation, oldest page last requested. */
  readonly loadOlderMessages: () => void;
}

const DEFAULT_MAX_RECONNECT_ATTEMPTS = 2;
const DEFAULT_RECONNECT_DELAY_MS = 1_000;

interface PendingSend {
  readonly clientMessageId: string;
  readonly text: string;
}

/** How one attempt at reading the stream ended. */
type TurnOutcome =
  /** A `done` or `error` frame arrived — the server said its piece. */
  | 'terminal'
  /** The user pressed Stop, or the component unmounted. */
  | 'aborted'
  /** The request itself failed with a typed envelope. */
  | 'failed'
  /** The body ended with no terminal frame. Retryable. */
  | 'dropped';

export function useChatStream(options: UseChatStreamOptions): ChatStreamController {
  const [state, dispatch] = useReducer(chatReducer, initialChatState);
  const [historyPage, setHistoryPage] = useState(0);
  const [hasMoreHistory, setHasMoreHistory] = useState(false);
  const [isLoadingHistory, setLoadingHistory] = useState(false);

  const {
    target,
    conversationId = null,
    loadHistoryOnMount = true,
    maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    reconnectDelayMs = DEFAULT_RECONNECT_DELAY_MS,
  } = options;

  // `target` is an object literal at most call sites, so its identity changes every render. The
  // path is its value, and depending on the value instead of the reference is what stops the
  // history effect from re-firing forever.
  const targetKey = chatMessagesPath(target);
  const targetRef = useRef(target);
  targetRef.current = target;

  const abortRef = useRef<AbortController | null>(null);
  const mountedRef = useRef(true);
  const lastEventIdRef = useRef<string | null>(null);
  const lastSendRef = useRef<PendingSend | null>(null);
  const conversationRef = useRef<string | null>(conversationId);
  conversationRef.current = state.conversationId ?? conversationId;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      // Leaving the screen cancels the turn. ADR 007: the server cancels the agent run when the
      // client disconnects, so an abandoned tab does not keep a model call running.
      abortRef.current?.abort();
    };
  }, []);

  const loadHistoryPage = useCallback(
    async (page: number) => {
      setLoadingHistory(true);
      try {
        const result = await fetchChatHistory({ target: targetRef.current, page });
        if (!mountedRef.current) {
          return;
        }
        dispatch({ type: 'history_loaded', conversationId: result.conversation_id, messages: result.items });
        setHistoryPage(page);
        setHasMoreHistory((page + 1) * result.page_size < result.total);
      } catch (error) {
        if (mountedRef.current) {
          dispatch({ type: 'stream_failed', error: toErrorPayload(error), clientMessageId: null });
        }
      } finally {
        if (mountedRef.current) {
          setLoadingHistory(false);
        }
      }
    },
    // `targetKey` rather than `target`: see the ref above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [targetKey],
  );

  useEffect(() => {
    if (loadHistoryOnMount) {
      void loadHistoryPage(0);
    }
  }, [loadHistoryOnMount, loadHistoryPage]);

  const readStream = useCallback(async (send: PendingSend, controller: AbortController): Promise<TurnOutcome> => {
    let terminal = false;
    try {
      const frames = streamChatMessage({
        target: targetRef.current,
        clientMessageId: send.clientMessageId,
        text: send.text,
        conversationId: conversationRef.current,
        lastEventId: lastEventIdRef.current,
        signal: controller.signal,
      });

      for await (const frame of frames) {
        if (!mountedRef.current) {
          return 'aborted';
        }
        if (frame.id !== null) {
          lastEventIdRef.current = frame.id;
        }
        terminal = terminal || isTerminal(frame);
        dispatch({ type: 'frame', frame });
      }
    } catch (error) {
      if (controller.signal.aborted || !mountedRef.current) {
        return 'aborted';
      }
      if (error instanceof ApiError) {
        // A non-2xx before the stream opened: the envelope still fitted in a status code.
        dispatch({ type: 'stream_failed', error: toErrorPayload(error), clientMessageId: send.clientMessageId });
        return 'failed';
      }
      // A `TypeError` from `fetch` is the browser's way of saying the network went away.
      return 'dropped';
    }

    return terminal ? 'terminal' : 'dropped';
  }, []);

  const runTurn = useCallback(
    async (send: PendingSend) => {
      for (let attempt = 0; attempt <= maxReconnectAttempts; attempt += 1) {
        const controller = new AbortController();
        abortRef.current = controller;
        if (attempt === 0) {
          // A retry stays in `reconnecting` until text actually resumes, which is what §7.4's
          // "Interrupted until the server confirms continuation" is describing.
          dispatch({ type: 'stream_opened' });
        }

        const outcome = await readStream(send, controller);
        if (!mountedRef.current) {
          return;
        }
        if (outcome !== 'dropped') {
          if (outcome === 'aborted') {
            dispatch({ type: 'stream_cancelled' });
          }
          return;
        }

        // Dropped. §7.4: label the partial response before trying again, so the user is never
        // shown a truncated answer that looks finished.
        dispatch({ type: 'stream_disconnected' });
        if (attempt === maxReconnectAttempts) {
          return;
        }
        dispatch({ type: 'stream_reconnecting' });
        await delay(reconnectDelayMs * (attempt + 1));
        if (!mountedRef.current) {
          return;
        }
      }
    },
    [maxReconnectAttempts, readStream, reconnectDelayMs],
  );

  const startTurn = useCallback(
    (send: PendingSend) => {
      lastSendRef.current = send;
      dispatch({
        type: 'user_message_sent',
        clientMessageId: send.clientMessageId,
        text: send.text,
        createdAt: new Date().toISOString(),
      });
      void runTurn(send);
    },
    [runTurn],
  );

  const send = useCallback(
    (text: string) => {
      const trimmed = text.trim();
      if (trimmed === '' || turnInFlight(state)) {
        return;
      }
      startTurn({ clientMessageId: newClientMessageId(), text: trimmed });
    },
    [startTurn, state],
  );

  const retry = useCallback(() => {
    const previous = lastSendRef.current;
    if (previous === null || turnInFlight(state)) {
      return;
    }
    // The same client message id, deliberately. The server has either committed this message —
    // in which case it answers with the echo instead of a second copy — or it has not, and this is
    // the first successful attempt.
    startTurn(previous);
  }, [startTurn, state]);

  const stop = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  const loadOlderMessages = useCallback(() => {
    if (hasMoreHistory && !isLoadingHistory) {
      void loadHistoryPage(historyPage + 1);
    }
  }, [hasMoreHistory, historyPage, isLoadingHistory, loadHistoryPage]);

  return useMemo(
    () => ({
      state,
      messages: state.messages,
      isStreaming: turnInFlight(state),
      error: state.error,
      hasMoreHistory,
      isLoadingHistory,
      send,
      retry,
      stop,
      loadOlderMessages,
    }),
    [hasMoreHistory, isLoadingHistory, loadOlderMessages, retry, send, state, stop],
  );
}

function isTerminal(frame: ChatFrame): boolean {
  return frame.event.type === 'done' || frame.event.type === 'error';
}

/**
 * Anything thrown on the way to or from the server, as the §6.1 envelope the UI already renders.
 *
 * An unrecognised throwable becomes `internal_error` rather than surfacing its `message`: a
 * `TypeError` string is a developer artefact, and putting one in a chat bubble is how internals
 * reach users.
 */
function toErrorPayload(error: unknown): ChatStreamErrorPayload {
  if (error instanceof ApiError) {
    return {
      code: error.code,
      message: error.message,
      details: error.details,
      requestId: error.requestId,
      i18nKey: error.i18nKey,
    };
  }
  return {
    code: 'internal_error',
    message: 'The conversation could not be reached.',
    details: {},
    requestId: null,
    i18nKey: i18nKeyForErrorCode('internal_error'),
  };
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}
