import type { ChatHistoryMessage } from '@/lib/api/chat-api';
import type { ChatFrame, ChatRole, ChatStreamErrorPayload } from '@/lib/api/chat-events';

/**
 * Conversation state, as a pure reducer (task 20: "Frontend event parser and state reducer tests").
 *
 * Everything here is a plain function of `(state, action)`. No React, no timers, no `fetch` — the
 * hook in `../hooks/use-chat-stream.ts` owns all of that and does nothing but feed this. The split
 * is what makes the hard cases testable as data: a delta arriving after a disconnect, a duplicate
 * frame replayed by a resume, the same client message id sent twice.
 *
 * ## The invariants worth stating
 *
 * 1. **A partial assistant turn is marked, never silently completed.** ADR 007 persists it with
 *    `status=interrupted`; the UI must say so rather than presenting a truncated answer as the
 *    whole answer.
 * 2. **One client message id is one message, forever.** Retry, resume and the server echo all
 *    converge on the same entry.
 * 3. **Replayed frames are idempotent.** After a reconnect with `Last-Event-ID`, the server may
 *    resend frames the client already applied; anything at or below `lastEventId` is dropped.
 */

export type { ChatRole };

/**
 * Delivery state of one bubble (design system §7.2, §7.3, §7.4).
 *
 * `stopped` and `interrupted` are deliberately different words for deliberately different events:
 * the user stopped generation, or the connection did. §7.4 asks for exactly that distinction, and
 * collapsing them would tell a user their network dropped when in fact they pressed Stop.
 */
export type ChatMessageStatus =
  /** Optimistic — shown immediately, not yet acknowledged by the server. */
  | 'pending'
  /** The server echoed it back and it is committed. */
  | 'sent'
  /** The send failed. §7.3: stays visible as "Not sent" with a retry that reuses the same id. */
  | 'failed'
  /** Assistant text is still arriving. */
  | 'streaming'
  | 'complete'
  /** Connection lost mid-turn. Partial text preserved and labelled. */
  | 'interrupted'
  /** The user pressed Stop. Partial text preserved and labelled. */
  | 'stopped';

export interface ChatMessage {
  /** Stable React key. Never changes once the message exists, even when the server id arrives. */
  readonly key: string;
  readonly clientMessageId: string | null;
  readonly messageId: string | null;
  readonly role: ChatRole;
  readonly text: string;
  readonly status: ChatMessageStatus;
  /**
   * The server's ordinal, or `null` while the message exists only optimistically.
   *
   * This is what the transcript is ordered by. `createdAt` cannot do it: `now()` is fixed for a
   * whole transaction in Postgres, so a turn's tool call, tool result and assistant reply all carry
   * the same timestamp and are unordered under it.
   */
  readonly seq: number | null;
  readonly createdAt: string | null;
}

/**
 * Tool activity, reduced to what a user may see.
 *
 * §7.2: "Never display internal tool identifiers or raw JSON." The argument deltas
 * (`tool_input_delta`) are therefore not accumulated anywhere in this state — there is nothing to
 * leak because nothing is kept. Only the fact that *something* is running survives, which is what
 * the "Comparing seasonal prices" style status is rendered from.
 */
export interface ChatToolActivity {
  readonly toolCallId: string;
  readonly name: string;
  readonly running: boolean;
}

export type ChatConnectionState =
  | 'idle'
  /** A turn is in flight — the request is open and frames are arriving. */
  | 'streaming'
  /** The connection dropped and a retry is scheduled. */
  | 'reconnecting'
  /** The turn finished normally. */
  | 'closed'
  /** A terminal `event: error`, or the request never got off the ground. */
  | 'failed';

export interface ChatState {
  readonly conversationId: string | null;
  readonly messages: readonly ChatMessage[];
  readonly connection: ChatConnectionState;
  /** ADR 007 resume position, sent as `Last-Event-ID` on reconnect. */
  readonly lastEventId: string | null;
  readonly error: ChatStreamErrorPayload | null;
  /** Set once by `trip_created`, so the route layer can hand off (PLAN §3.2). */
  readonly tripCreatedId: string | null;
  /**
   * Set by `brief_updated` (task 22, UC-C5-09) so the trip shell can re-fetch the brief and detail.
   * Unlike `tripCreatedId` this is a per-turn signal, not a one-off: it is cleared on `stream_opened`
   * so a second turn that edits the same trip re-fires the invalidation instead of being swallowed
   * by an unchanged value.
   */
  readonly briefUpdatedTripId: string | null;
  readonly tools: readonly ChatToolActivity[];
  readonly historyLoaded: boolean;
}

export const initialChatState: ChatState = {
  conversationId: null,
  messages: [],
  connection: 'idle',
  lastEventId: null,
  error: null,
  tripCreatedId: null,
  briefUpdatedTripId: null,
  tools: [],
  historyLoaded: false,
};

export type ChatAction =
  | {
      readonly type: 'history_loaded';
      readonly conversationId: string | null;
      readonly messages: readonly ChatHistoryMessage[];
    }
  | {
      readonly type: 'user_message_sent';
      readonly clientMessageId: string;
      readonly text: string;
      readonly createdAt: string;
    }
  | { readonly type: 'stream_opened' }
  | { readonly type: 'stream_reconnecting' }
  | { readonly type: 'frame'; readonly frame: ChatFrame }
  /** The body ended without a `done` frame — a dropped connection, not a finished turn. */
  | { readonly type: 'stream_disconnected' }
  | { readonly type: 'stream_cancelled' }
  | { readonly type: 'stream_failed'; readonly error: ChatStreamErrorPayload; readonly clientMessageId: string | null };

export function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'history_loaded':
      return loadHistory(state, action.conversationId, action.messages);
    case 'user_message_sent':
      return sendUserMessage(state, action.clientMessageId, action.text, action.createdAt);
    case 'stream_opened':
      // `briefUpdatedTripId` is cleared here so a brief_updated in this turn is a fresh null→id
      // transition the panel's effect will act on, even when the same trip was edited last turn.
      return { ...state, connection: 'streaming', error: null, briefUpdatedTripId: null };
    case 'stream_reconnecting':
      return { ...state, connection: 'reconnecting' };
    case 'frame':
      return applyFrame(state, action.frame);
    case 'stream_disconnected':
      return { ...state, connection: 'closed', messages: markOpenTurn(state.messages, 'interrupted'), tools: [] };
    case 'stream_cancelled':
      return { ...state, connection: 'closed', messages: markOpenTurn(state.messages, 'stopped'), tools: [] };
    case 'stream_failed':
      return failStream(state, action.error, action.clientMessageId);
    default:
      return exhausted(action);
  }
}

/**
 * Compile-time proof that the switch above is total.
 *
 * A new action added without a case lands here, and `never` makes it a build failure rather than a
 * silently ignored user interaction.
 */
function exhausted(action: never): never {
  throw new Error(`Unhandled chat action: ${JSON.stringify(action)}`);
}

// -------------------------------------------------------------------------------------------
// History
// -------------------------------------------------------------------------------------------

/**
 * Merges one page of history into the conversation.
 *
 * <b>Merge, not replace</b>, because history is paginated: loading the previous page must not
 * discard the page already on screen, and re-loading a page the user has already seen must not
 * double it. Committed messages are keyed on `message_id`, so the operation is idempotent however
 * many times a page arrives.
 *
 * Two things survive the merge deliberately:
 *
 * - **A message still in flight.** A page load is authoritative about what is committed, not about
 *   what the user typed two seconds ago. Dropping a `pending` bubble would make a user's own words
 *   vanish and then reappear.
 * - **A turn currently streaming.** History says it is `interrupted` or absent; the live stream
 *   knows better, and letting a background reload rewind a response mid-sentence would be visible.
 */
function loadHistory(
  state: ChatState,
  conversationId: string | null,
  history: readonly ChatHistoryMessage[],
): ChatState {
  const committed = new Map<string, ChatMessage>();
  for (const message of state.messages) {
    if (message.messageId !== null) {
      committed.set(message.messageId, message);
    }
  }
  for (const persisted of history) {
    if (!isRenderable(persisted.role)) {
      continue;
    }
    const existing = committed.get(persisted.message_id);
    committed.set(persisted.message_id, existing?.status === 'streaming' ? existing : toMessage(persisted, existing));
  }

  const ordered = [...committed.values()].sort(bySeq);
  const inFlight = state.messages.filter(
    (message) => message.messageId === null && (message.status === 'pending' || message.status === 'failed'),
  );

  return {
    ...state,
    conversationId: conversationId ?? state.conversationId,
    messages: [...ordered, ...inFlight],
    historyLoaded: true,
  };
}

/**
 * Which of the six wire roles reach the transcript.
 *
 * `tool_call` and `tool_result` do not. Their `content` is the tool's JSON, and design system §7.2
 * forbids showing internal tool identifiers or raw JSON — the live stream already keeps only "some
 * tool is running" (see `ChatToolActivity`), and a reloaded page has to agree with the live one or
 * refreshing would reveal what streaming hid.
 *
 * Dropping them here rather than in the schema is the whole point of the split: they still *parse*,
 * so a page containing one loads instead of failing, and only the rendering opts out.
 */
function isRenderable(role: ChatHistoryMessage['role']): role is ChatRole {
  return role !== 'tool_call' && role !== 'tool_result';
}

function toMessage(persisted: ChatHistoryMessage, existing?: ChatMessage): ChatMessage {
  return {
    // The key of an entry already on screen is preserved so React does not remount the bubble —
    // the optimistic message and its persisted form are the same message.
    key: existing?.key ?? persisted.client_message_id ?? persisted.message_id,
    clientMessageId: persisted.client_message_id ?? existing?.clientMessageId ?? null,
    messageId: persisted.message_id,
    role: renderableRole(persisted.role),
    text: persisted.content,
    status: persistedStatus(persisted),
    seq: persisted.seq,
    createdAt: persisted.created_at,
  };
}

/**
 * Narrows a wire role to a renderable one.
 *
 * Only ever called behind {@link isRenderable}, so the fallback is unreachable — it exists because
 * a cast would silently accept a seventh role added to the contract later, and this way the value
 * lands on `system` (a neutral note) instead of on a sender label that does not exist.
 */
function renderableRole(role: ChatHistoryMessage['role']): ChatRole {
  return isRenderable(role) ? role : 'system';
}

function persistedStatus(persisted: ChatHistoryMessage): ChatMessageStatus {
  if (persisted.status === 'interrupted') {
    return 'interrupted';
  }
  return persisted.role === 'user' ? 'sent' : 'complete';
}

/**
 * Orders the transcript by the server's ordinal.
 *
 * `null` sorts last: an entry with no `seq` is one the server has not committed, so it belongs after
 * everything that has. This replaced a comparator over `createdAt`, which returned 0 for every row
 * of the same turn — Postgres stamps them identically — and then relied on `Array.prototype.sort`
 * being stable to preserve whatever order the response happened to arrive in.
 */
function bySeq(left: ChatMessage, right: ChatMessage): number {
  if (left.seq === right.seq) {
    return 0;
  }
  if (left.seq === null) {
    return 1;
  }
  if (right.seq === null) {
    return -1;
  }
  return left.seq - right.seq;
}

// -------------------------------------------------------------------------------------------
// Optimistic sends and de-duplication
// -------------------------------------------------------------------------------------------

/**
 * Adds the optimistic bubble — or revives the existing one on a retry.
 *
 * <b>This is the de-duplication rule, and it is half of the DoD item.</b> A retry reuses the client
 * message id (§7.3: "Retry reuses the stable client message ID and must not create a duplicate
 * message"), so a second send of a known id updates that entry in place instead of appending. The
 * other half is `message_start` below, which reconciles the server's echo onto the same entry
 * rather than appending a copy of what the user is already looking at.
 */
function sendUserMessage(state: ChatState, clientMessageId: string, text: string, createdAt: string): ChatState {
  const existing = state.messages.find((message) => message.clientMessageId === clientMessageId);

  if (existing) {
    return {
      ...state,
      error: null,
      messages: state.messages.map((message) =>
        message.clientMessageId === clientMessageId ? { ...message, text, status: 'pending' } : message,
      ),
    };
  }

  const optimistic: ChatMessage = {
    key: clientMessageId,
    clientMessageId,
    messageId: null,
    role: 'user',
    text,
    status: 'pending',
    // No ordinal until the server commits the row; bySeq puts an uncommitted bubble last, which is
    // where the user's just-typed message belongs.
    seq: null,
    createdAt,
  };

  return { ...state, error: null, messages: [...state.messages, optimistic] };
}

// -------------------------------------------------------------------------------------------
// Frames
// -------------------------------------------------------------------------------------------

function applyFrame(state: ChatState, frame: ChatFrame): ChatState {
  if (isReplayed(state.lastEventId, frame.id)) {
    // ADR 007: "Client must handle out-of-order/duplicate frames idempotently after reconnect."
    // The server replays from `Last-Event-ID`, and an off-by-one there must not double a word.
    return state;
  }

  const advanced = frame.id === null ? state : { ...state, lastEventId: frame.id };
  return applyEvent(advanced, frame);
}

/** True when this frame's id is not ahead of the last one applied. Non-numeric ids never replay. */
function isReplayed(lastEventId: string | null, frameId: string | null): boolean {
  if (lastEventId === null || frameId === null) {
    return false;
  }
  const last = Number(lastEventId);
  const current = Number(frameId);
  if (!Number.isFinite(last) || !Number.isFinite(current)) {
    return false;
  }
  return current <= last;
}

function applyEvent(state: ChatState, frame: ChatFrame): ChatState {
  const event = frame.event;

  switch (event.type) {
    case 'message_start':
      return startMessage(state, event.messageId, event.role, event.clientMessageId, event.content, event.createdAt);
    case 'text_delta':
      return appendDelta(state, event.messageId, event.text);
    case 'message_end':
      return endMessage(state, event.messageId, event.status);
    case 'tool_use_start':
      return { ...state, tools: [...state.tools, { toolCallId: event.toolCallId, name: event.name, running: true }] };
    case 'tool_use_end':
    case 'tool_result':
      return { ...state, tools: stopTool(state.tools, event.toolCallId) };
    case 'tool_input_delta':
      // Deliberately dropped. §7.2 forbids showing raw tool JSON, and the safest way to guarantee
      // that is to never hold it.
      return state;
    case 'trip_created':
      return { ...state, tripCreatedId: event.tripId };
    case 'brief_updated':
      // The brief the trip shell is showing is now stale; the panel re-fetches on this transition.
      return { ...state, briefUpdatedTripId: event.tripId };
    case 'usage':
      // Token accounting is a server-side concern (`ai_call_log`, PLAN §5.3). Nothing in the chat
      // UI renders it, so it is not kept.
      return state;
    case 'done':
      return { ...state, connection: 'closed', messages: markOpenTurn(state.messages, 'complete'), tools: [] };
    case 'error':
      return failStream(state, event.error, null);
    case 'heartbeat':
      // ADR 007's 15s `: ping`. Its only job is to keep the connection and any proxy awake.
      return state;
    case 'ignored':
      // Already explained by the parser (`unknown_event`, `malformed_json`, …). Carrying it into
      // the conversation would put a diagnostic in front of a user; dropping the whole stream over
      // it would be worse. Neither: the turn continues.
      return state;
    default:
      return exhaustedEvent(event);
  }
}

function exhaustedEvent(event: never): never {
  throw new Error(`Unhandled chat event: ${JSON.stringify(event)}`);
}

/**
 * Opens a message, or reconciles the echo of one already on screen.
 *
 * Three cases, in order:
 * 1. The echo of an optimistic user message — matched on `clientMessageId`, updated in place.
 * 2. A message id already present — a replayed frame after a resume; ignored.
 * 3. Anything else — a new bubble.
 */
function startMessage(
  state: ChatState,
  messageId: string,
  role: ChatRole,
  clientMessageId: string | null,
  content: string | null,
  createdAt: string | null,
): ChatState {
  if (clientMessageId !== null) {
    const optimistic = state.messages.find((message) => message.clientMessageId === clientMessageId);
    if (optimistic) {
      return {
        ...state,
        messages: state.messages.map((message) =>
          message.clientMessageId === clientMessageId
            ? {
                ...message,
                messageId,
                // The server's copy wins if it sent one — it is what was persisted, and it may
                // have been trimmed or normalised on the way in.
                text: content ?? message.text,
                status: role === 'user' ? 'sent' : message.status,
                createdAt: createdAt ?? message.createdAt,
              }
            : message,
        ),
      };
    }
  }

  if (state.messages.some((message) => message.messageId === messageId)) {
    return state;
  }

  const opened: ChatMessage = {
    key: clientMessageId ?? messageId,
    clientMessageId,
    messageId,
    role,
    text: content ?? '',
    status: role === 'assistant' ? 'streaming' : 'sent',
    // ADR 007's frames carry the ordinal as the SSE event id rather than in the payload, so a
    // streaming message has none until history is next read. Ordering is not at risk: a live turn is
    // appended, and appended is where it goes.
    seq: null,
    createdAt,
  };

  return { ...state, messages: [...state.messages, opened] };
}

/**
 * Appends assistant text.
 *
 * Targets the message the frame names; falls back to the open assistant turn when the server omits
 * `message_id`, and opens one if there is none. The fallback exists because a delta with nowhere to
 * go is text a user typed a question to receive — dropping it is a worse failure than tolerating a
 * server that skipped `message_start`.
 */
function appendDelta(state: ChatState, messageId: string | null, text: string): ChatState {
  const index = findTargetIndex(state, messageId);

  // Text arriving is the proof that a reconnect succeeded — §7.4 keeps the response labelled
  // "Interrupted" until exactly this moment.
  const connection = 'streaming' as const;

  if (index === -1) {
    const opened: ChatMessage = {
      key: messageId ?? `assistant-${state.messages.length}`,
      clientMessageId: null,
      messageId,
      role: 'assistant',
      text,
      status: 'streaming',
      seq: null,
      createdAt: null,
    };
    return { ...state, connection, messages: [...state.messages, opened] };
  }

  return {
    ...state,
    connection,
    messages: state.messages.map((message, position) =>
      position === index ? { ...message, text: message.text + text, status: 'streaming' } : message,
    ),
  };
}

function findTargetIndex(state: ChatState, messageId: string | null): number {
  if (messageId !== null) {
    const byId = state.messages.findIndex((message) => message.messageId === messageId);
    if (byId !== -1) {
      return byId;
    }
  }

  const open = findOpenTurnIndex(state.messages);
  if (open !== -1) {
    return open;
  }

  // Resume without a `message_id` (ADR 007). The turn was marked `interrupted` when the connection
  // dropped; text arriving *while reconnecting* is its continuation, not a new answer. The
  // connection check is what keeps this from resurrecting an interrupted turn from an earlier one.
  return state.connection === 'reconnecting' ? findLastAssistantIndex(state.messages, 'interrupted') : -1;
}

/** The assistant turn currently receiving text, if any. There is at most one. */
function findOpenTurnIndex(messages: readonly ChatMessage[]): number {
  return findLastAssistantIndex(messages, 'streaming');
}

function findLastAssistantIndex(messages: readonly ChatMessage[], status: ChatMessageStatus): number {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message && message.role === 'assistant' && message.status === status) {
      return index;
    }
  }
  return -1;
}

function endMessage(state: ChatState, messageId: string, status: 'complete' | 'interrupted'): ChatState {
  return {
    ...state,
    messages: state.messages.map((message) => (message.messageId === messageId ? { ...message, status } : message)),
  };
}

/**
 * Closes whatever turn was still open.
 *
 * This is the "explicitly marked rather than silently completed" rule in one place: whether the
 * cause was a clean `done`, a dropped socket, or the Stop button, the open bubble always ends up
 * with a status that says which — and its text is never truncated or discarded.
 */
function markOpenTurn(messages: readonly ChatMessage[], status: ChatMessageStatus): readonly ChatMessage[] {
  const index = findOpenTurnIndex(messages);
  if (index === -1) {
    return messages;
  }
  return messages.map((message, position) => (position === index ? { ...message, status } : message));
}

function stopTool(tools: readonly ChatToolActivity[], toolCallId: string): readonly ChatToolActivity[] {
  return tools.map((tool) => (tool.toolCallId === toolCallId ? { ...tool, running: false } : tool));
}

/**
 * A typed failure, from either side of the `200`.
 *
 * The partial assistant turn is marked `interrupted` rather than removed — the user should keep
 * what did arrive — and the user message that triggered the turn is marked `failed` so §7.3's
 * "Not sent" affordance can offer a retry on the same client message id.
 */
function failStream(state: ChatState, error: ChatStreamErrorPayload, clientMessageId: string | null): ChatState {
  const withFailedSend = state.messages.map((message) =>
    clientMessageId !== null && message.clientMessageId === clientMessageId
      ? { ...message, status: 'failed' as ChatMessageStatus }
      : message,
  );

  return {
    ...state,
    connection: 'failed',
    error,
    tools: [],
    messages: markOpenTurn(withFailedSend, 'interrupted'),
  };
}

/** True while a turn is in flight — what the composer's Send/Stop swap is driven from (§7.3). */
export function isStreaming(state: ChatState): boolean {
  return state.connection === 'streaming' || state.connection === 'reconnecting';
}
