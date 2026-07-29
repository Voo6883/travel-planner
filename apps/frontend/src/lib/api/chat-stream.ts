import { ApiError, apiErrorFromResponse } from './api-error';
import { chatMessagesPath, type ChatTarget } from './chat-api';
import { ChatFrameDecoder, type ChatFrame } from './chat-events';
import { apiBaseUrl, csrfToken, CSRF_HEADER, newRequestId, REQUEST_ID_HEADER } from './client';

/**
 * The chat stream client (ADR 007).
 *
 * <b>This file is the documented exception to "no raw fetch" (PLAN §4.2, §13.2-F6).</b> The
 * exemption covers this file only and must be cited in review. The reason is structural rather
 * than stylistic:
 *
 * - `EventSource` is GET-only and cannot set headers, so it can carry neither the message body nor
 *   the `X-XSRF-TOKEN` that ADR 006 requires. It is not an option, not merely a worse one.
 * - `apiRequest()` reads the whole body and parses it as JSON. That is exactly right for every
 *   other endpoint and exactly wrong for a response that is designed never to finish.
 *
 * What this file does *not* do is re-implement the cross-cutting concerns. Base URL, credentials,
 * CSRF and correlation are imported from `client.ts`, so a change to the CSRF header name or the
 * same-origin rule reaches the chat stream too instead of leaving one forgotten caller behind.
 */

export interface SendChatMessageRequest {
  readonly target: ChatTarget;
  /**
   * Client-generated, stable across retries. ADR 007 / task 20 DoD: "Disconnect/retry cannot
   * duplicate committed user messages" — the server treats this as an idempotency key, and the
   * client reconciles its optimistic bubble against the echo carrying the same value.
   */
  readonly clientMessageId: string;
  readonly text: string;
  /** Continues an existing conversation. Absent on the first planner turn, which creates one. */
  readonly conversationId?: string | null;
  /**
   * Resume position (ADR 007). The server replays persisted frames after this id and then
   * continues, which is what makes a mid-turn reconnect invisible rather than a lost response.
   */
  readonly lastEventId?: string | null;
  readonly signal?: AbortSignal;
}

/**
 * `text/event-stream` selects the streaming operation; `application/json` is what makes a refusal
 * readable.
 *
 * Content negotiation applies to the error response too. With the stream type alone the server has
 * no converter that can write the §6.1 envelope, so a `400` or a `404` arrives as a status with an
 * empty body — the one shape `apiErrorFromResponse` cannot turn into something a user can act on.
 * Both types are published on the operation in `openapi.yaml`, and
 * `ChatControllerTest.aClientAcceptingOnlyTheStreamCannotBeToldWhyItsRequestWasRefused` pins the
 * behaviour on the server side so this header cannot be narrowed by accident.
 */
export const SSE_ACCEPT_HEADER = 'text/event-stream, application/json';
export const LAST_EVENT_ID_HEADER = 'Last-Event-ID';

/**
 * Opens a turn and yields frames as they arrive.
 *
 * An async generator rather than a callback: cancellation, back-pressure and `try/finally` cleanup
 * all come from the language instead of from a hand-rolled subscription protocol, and the caller
 * can `break` out of the loop without the reader leaking.
 *
 * Failure modes, kept deliberately distinct because the UI reacts differently to each:
 *
 * | Situation | What happens here |
 * |---|---|
 * | Non-2xx before the stream starts | throws `ApiError` — the envelope still fits in a status |
 * | `event: error` mid-stream | yielded as a frame; ADR 007 forbids a bare abort |
 * | Connection dropped mid-stream | the generator returns; the caller marks the turn interrupted |
 * | `AbortSignal` fired | `fetch`/`read` reject with `AbortError`, which is re-thrown unchanged |
 */
export async function* streamChatMessage(request: SendChatMessageRequest): AsyncGenerator<ChatFrame> {
  const response = await fetch(`${apiBaseUrl()}${chatMessagesPath(request.target)}`, {
    method: 'POST',
    signal: request.signal,
    credentials: 'include',
    cache: 'no-store',
    headers: streamHeaders(request),
    body: JSON.stringify({
      client_message_id: request.clientMessageId,
      content: request.text,
      ...(request.conversationId ? { conversation_id: request.conversationId } : {}),
    }),
  });

  if (!response.ok) {
    throw await apiErrorFromResponse(response);
  }

  const body = response.body;
  if (!body) {
    // A 200 with no readable body is not a stream. Surfacing it as the standard envelope keeps the
    // UI on one error path instead of adding a second, undocumented failure mode.
    throw new ApiError({
      status: response.status,
      code: 'internal_error',
      message: 'The chat stream returned no body.',
    });
  }

  const reader = body.getReader();
  const bytes = new TextDecoder();
  const frames = new ChatFrameDecoder();

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        yield* frames.flush();
        return;
      }
      // `stream: true` keeps a multi-byte character split across two chunks intact. Without it a
      // UTF-8 sequence straddling a chunk boundary decodes to U+FFFD — visible to a user as a
      // corrupted character in the middle of a streamed word.
      yield* frames.push(bytes.decode(value, { stream: true }));
    }
  } finally {
    reader.releaseLock();
  }
}

function streamHeaders(request: SendChatMessageRequest): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: SSE_ACCEPT_HEADER,
    'Content-Type': 'application/json',
    [REQUEST_ID_HEADER]: newRequestId(),
  };

  // POST is a mutating method, so CSRF applies exactly as it does through `apiRequest`.
  const token = csrfToken();
  if (token) {
    headers[CSRF_HEADER] = token;
  }

  if (request.lastEventId) {
    headers[LAST_EVENT_ID_HEADER] = request.lastEventId;
  }

  return headers;
}
