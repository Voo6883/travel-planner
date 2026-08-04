import { z } from 'zod';
import { ApiError, i18nKeyForErrorCode } from './api-error';

/**
 * The chat event union and its wire parser (ADR 007).
 *
 * <b>Hand-authored on purpose.</b> ADR 007 records the reason: no OpenAPI generator emits a usable
 * `text/event-stream` client, so the most safety-critical type in the app cannot come from
 * `npm run codegen`. It is instead mirrored by the backend's `LlmEvent` sealed interface, so drift
 * is a compile error on at least one side. This file and `chat-stream.ts` are the whole exception —
 * every non-streaming chat body still goes through the generated contract.
 *
 * Nothing here touches React or `fetch`. Parsing an SSE byte stream is where the classic bugs live
 * (a frame split mid-JSON across two chunks; an event name nobody has implemented yet), and those
 * are only testable if the parser is a pure function of the text it was handed.
 *
 * ## Two rules the parser will not bend
 *
 * 1. **An unrecognised frame is never text.** It becomes an explicit `ignored` event carrying a
 *    reason. Falling back to "treat the payload as assistant prose" is how a future `event:
 *    reasoning` frame would end up rendered to a user as the model's hidden chain of thought —
 *    which task 20 forbids outright.
 * 2. **A malformed frame never ends the stream.** It is reported as `ignored` and the reader keeps
 *    going. A terminal failure arrives as `event: error` and only as `event: error` (ADR 007:
 *    "never a bare stream abort").
 */

/** Assistant text arrived, token by token. */
export interface TextDeltaEvent {
  readonly type: 'text_delta';
  readonly messageId: string | null;
  readonly text: string;
}

/**
 * The roles a transcript can render.
 *
 * Narrower than `ChatMessageRole` in `chat-api.ts`, which is the six-value *wire* vocabulary, and
 * deliberately so: `tool_call` and `tool_result` carry raw JSON in `content`, and design system §7.2
 * forbids showing internal tool identifiers or raw JSON to a user. They are parsed (a page
 * containing one must not fail) and then dropped by the reducer.
 *
 * `lifecycle_event` is ADR 007's `DomainEvent` — `trip_created` and its successors. It is a thing
 * that happened to the trip, it is meant to be visible in the thread, and it has a sender label.
 */
export type ChatRole = 'user' | 'assistant' | 'system' | 'lifecycle_event';

/**
 * A message opened.
 *
 * `clientMessageId` is present on the server's echo of a user message and is what de-duplication
 * reconciles against — see `features/chat/lib/chat-state.ts`. `content` is present only when the
 * whole message is known up front (that same echo, and history replay after a resume); an
 * assistant turn arrives as deltas instead.
 */
export interface MessageStartEvent {
  readonly type: 'message_start';
  readonly messageId: string;
  readonly role: ChatRole;
  readonly clientMessageId: string | null;
  readonly content: string | null;
  readonly createdAt: string | null;
}

/** How a message finished. `interrupted` is ADR 007's persisted partial turn. */
export type MessageEndStatus = 'complete' | 'interrupted';

export interface MessageEndEvent {
  readonly type: 'message_end';
  readonly messageId: string;
  readonly status: MessageEndStatus;
}

export interface ToolUseStartEvent {
  readonly type: 'tool_use_start';
  readonly toolCallId: string;
  readonly name: string;
}

export interface ToolInputDeltaEvent {
  readonly type: 'tool_input_delta';
  readonly toolCallId: string;
  readonly jsonChunk: string;
}

export interface ToolUseEndEvent {
  readonly type: 'tool_use_end';
  readonly toolCallId: string;
}

export interface ToolResultEvent {
  readonly type: 'tool_result';
  readonly toolCallId: string;
  readonly payload: unknown;
}

/**
 * The handoff event (PLAN §3.2). Emitted by the orchestrator *after* `create_trip` commits — never
 * parsed out of assistant prose, which is why it is a frame of its own rather than a marker in
 * text.
 */
export interface TripCreatedEvent {
  readonly type: 'trip_created';
  readonly tripId: string;
}

/**
 * The trip's brief was written by an intake tool (task 22, UC-C5-09). Unlike `trip_created` this is
 * not a navigation cue — the trip already exists — it tells the client the brief and status it is
 * showing are stale. Emitted by the orchestrator only after `update_trip_brief` or
 * `answer_clarification` commits, never parsed out of prose.
 */
export interface BriefUpdatedEvent {
  readonly type: 'brief_updated';
  readonly tripId: string;
}

/** Research started from chat (task 27, UC-C5-03). */
export interface ResearchStartedEvent {
  readonly type: 'research_started';
  readonly tripId: string;
  readonly jobId: string;
}

/** Destination selected from chat (task 27, UC-C5-05). */
export interface DestinationSelectedEvent {
  readonly type: 'destination_selected';
  readonly tripId: string;
}

export interface UsageEvent {
  readonly type: 'usage';
  readonly inputTokens: number;
  readonly outputTokens: number;
  readonly cachedTokens: number | null;
}

/** The turn finished normally. The server closes after this. */
export interface DoneEvent {
  readonly type: 'done';
  readonly stopReason: string | null;
}

/**
 * The §6.1 envelope, delivered in-band.
 *
 * Once a stream has returned `200` the envelope can no longer travel as an HTTP status, so it
 * travels as a frame. `requestId` is in the body here rather than in `X-Request-Id`: headers were
 * flushed long before the failure happened.
 */
export interface ChatStreamErrorPayload {
  readonly code: string;
  /** English developer string. §6.1: never rendered — resolve `i18nKey` instead. */
  readonly message: string;
  readonly details: Record<string, unknown>;
  readonly requestId: string | null;
  /** `common.errors.<code>`, falling back to `internal_error` for an unregistered code. */
  readonly i18nKey: string;
}

export interface StreamErrorEvent {
  readonly type: 'error';
  readonly error: ChatStreamErrorPayload;
}

/** A `: ping` comment (ADR 007: every 15s). Proof of liveness, carries nothing. */
export interface HeartbeatEvent {
  readonly type: 'heartbeat';
}

/** Why a frame produced nothing. Every value here is a decision, not an accident. */
export type IgnoredReason =
  /** An `event:` name this client version does not implement. Forward compatibility. */
  | 'unknown_event'
  /** `data:` was not JSON. */
  | 'malformed_json'
  /** JSON, but not the shape the event declares. */
  | 'invalid_payload'
  /** A frame with data but no `event:` name. SSE would default it to `message`; we will not. */
  | 'missing_event_name'
  /** `event:` with no `data:` line at all. */
  | 'missing_data'
  /** The stream ended part-way through a frame. Per the SSE spec, pending data is discarded. */
  | 'incomplete_frame';

export interface IgnoredEvent {
  readonly type: 'ignored';
  readonly reason: IgnoredReason;
  /** The `event:` name as it arrived, for logging. Never rendered. */
  readonly eventName: string | null;
}

export type ChatStreamEvent =
  | TextDeltaEvent
  | MessageStartEvent
  | MessageEndEvent
  | ToolUseStartEvent
  | ToolInputDeltaEvent
  | ToolUseEndEvent
  | ToolResultEvent
  | TripCreatedEvent
  | BriefUpdatedEvent
  | ResearchStartedEvent
  | DestinationSelectedEvent
  | UsageEvent
  | DoneEvent
  | StreamErrorEvent
  | HeartbeatEvent
  | IgnoredEvent;

/**
 * One decoded frame.
 *
 * `id` is ADR 007's monotonic per-conversation sequence. It is kept beside the event rather than
 * inside it because it is a transport concern — resume position and duplicate suppression — that
 * every event type shares.
 */
export interface ChatFrame {
  readonly id: string | null;
  readonly event: ChatStreamEvent;
}

// -------------------------------------------------------------------------------------------
// Payload schemas. The `event:` name selects one; zod decides whether the bytes match it.
// -------------------------------------------------------------------------------------------

/**
 * Exactly the values ADR 007 publishes — no case normalisation (STATUS F-31, now closed).
 *
 * The backend's constants really are `USER`/`ASSISTANT`/`SYSTEM`, but the conversion to the wire
 * now happens once, in `api/dto/chat/ChatWireNames`, and `ChatWireNamesTest` asserts it for every
 * constant of both chat enums. This parser used to lower-case defensively because nothing had
 * decided; accepting both cases now would mean an upper-case regression reached a user as a working
 * conversation here and a broken one everywhere else.
 */
const roleSchema = z.union([z.literal('user'), z.literal('assistant'), z.literal('system')]);

const textDeltaSchema = z.object({ text: z.string(), message_id: z.string().nullish() });

const messageStartSchema = z.object({
  message_id: z.string(),
  role: roleSchema,
  client_message_id: z.string().nullish(),
  content: z.string().nullish(),
  created_at: z.string().nullish(),
});

const messageEndSchema = z.object({
  message_id: z.string(),
  // Any string. The backend has four statuses (`STREAMING`/`COMPLETE`/`INTERRUPTED`/`FAILED`) and
  // the UI needs two, so the collapse happens in the parser below rather than in the schema.
  status: z.string().nullish(),
});

const toolUseStartSchema = z.object({ tool_call_id: z.string(), name: z.string() });
const toolInputDeltaSchema = z.object({ tool_call_id: z.string(), json_chunk: z.string() });
const toolUseEndSchema = z.object({ tool_call_id: z.string() });
const toolResultSchema = z.object({ tool_call_id: z.string(), payload: z.unknown() });
const tripCreatedSchema = z.object({ trip_id: z.string() });
const briefUpdatedSchema = z.object({ trip_id: z.string() });
const researchStartedSchema = z.object({ trip_id: z.string(), job_id: z.string() });
const destinationSelectedSchema = z.object({ trip_id: z.string() });

const usageSchema = z.object({
  input_tokens: z.number(),
  output_tokens: z.number(),
  cached_tokens: z.number().nullish(),
});

const doneSchema = z.object({ stop_reason: z.string().nullish() });

const errorSchema = z.object({
  code: z.string(),
  message: z.string().nullish(),
  details: z.record(z.unknown()).nullish(),
  request_id: z.string().nullish(),
});

/**
 * Every wire name this client understands, mapped to the event it becomes.
 *
 * Adding a member to the backend's `LlmEvent` sealed interface without adding a row here degrades
 * to `ignored: unknown_event` — visible in logs, harmless to the user, and never mistaken for
 * assistant text.
 */
const EVENT_PARSERS: Record<string, (data: unknown) => ChatStreamEvent | null> = {
  text_delta: (data) => {
    const parsed = textDeltaSchema.safeParse(data);
    return parsed.success
      ? { type: 'text_delta', text: parsed.data.text, messageId: parsed.data.message_id ?? null }
      : null;
  },
  message_start: (data) => {
    const parsed = messageStartSchema.safeParse(data);
    if (!parsed.success) {
      return null;
    }
    return {
      type: 'message_start',
      messageId: parsed.data.message_id,
      role: parsed.data.role,
      clientMessageId: parsed.data.client_message_id ?? null,
      content: parsed.data.content ?? null,
      createdAt: parsed.data.created_at ?? null,
    };
  },
  message_end: (data) => {
    const parsed = messageEndSchema.safeParse(data);
    if (!parsed.success) {
      return null;
    }
    return { type: 'message_end', messageId: parsed.data.message_id, status: endStatus(parsed.data.status) };
  },
  tool_use_start: (data) => {
    const parsed = toolUseStartSchema.safeParse(data);
    return parsed.success
      ? { type: 'tool_use_start', toolCallId: parsed.data.tool_call_id, name: parsed.data.name }
      : null;
  },
  tool_input_delta: (data) => {
    const parsed = toolInputDeltaSchema.safeParse(data);
    if (!parsed.success) {
      return null;
    }
    return { type: 'tool_input_delta', toolCallId: parsed.data.tool_call_id, jsonChunk: parsed.data.json_chunk };
  },
  tool_use_end: (data) => {
    const parsed = toolUseEndSchema.safeParse(data);
    return parsed.success ? { type: 'tool_use_end', toolCallId: parsed.data.tool_call_id } : null;
  },
  tool_result: (data) => {
    const parsed = toolResultSchema.safeParse(data);
    return parsed.success
      ? { type: 'tool_result', toolCallId: parsed.data.tool_call_id, payload: parsed.data.payload }
      : null;
  },
  trip_created: (data) => {
    const parsed = tripCreatedSchema.safeParse(data);
    return parsed.success ? { type: 'trip_created', tripId: parsed.data.trip_id } : null;
  },
  brief_updated: (data) => {
    const parsed = briefUpdatedSchema.safeParse(data);
    return parsed.success ? { type: 'brief_updated', tripId: parsed.data.trip_id } : null;
  },
  research_started: (data) => {
    const parsed = researchStartedSchema.safeParse(data);
    return parsed.success ? { type: 'research_started', tripId: parsed.data.trip_id, jobId: parsed.data.job_id } : null;
  },
  destination_selected: (data) => {
    const parsed = destinationSelectedSchema.safeParse(data);
    return parsed.success ? { type: 'destination_selected', tripId: parsed.data.trip_id } : null;
  },
  usage: (data) => {
    const parsed = usageSchema.safeParse(data);
    if (!parsed.success) {
      return null;
    }
    return {
      type: 'usage',
      inputTokens: parsed.data.input_tokens,
      outputTokens: parsed.data.output_tokens,
      cachedTokens: parsed.data.cached_tokens ?? null,
    };
  },
  done: (data) => {
    const parsed = doneSchema.safeParse(data);
    return parsed.success ? { type: 'done', stopReason: parsed.data.stop_reason ?? null } : null;
  },
  error: (data) => {
    const parsed = errorSchema.safeParse(data);
    if (!parsed.success) {
      return null;
    }
    return { type: 'error', error: toErrorPayload(parsed.data) };
  },
};

/**
 * Collapses the backend's four message statuses onto the two a reader can act on.
 *
 * `interrupted` and `failed` both mean "this is not the whole answer"; an absent status means the
 * server did not think the distinction applied, which for a turn that reached `message_end`
 * normally is `complete`.
 *
 * The collapse stays and the case normalisation that used to sit beside it is gone (STATUS F-31):
 * four statuses onto two is this layer's decision, while lower-casing was compensation for an
 * unsettled contract. The wire is lower-case now, asserted on the server for every constant.
 */
function endStatus(status: string | null | undefined): MessageEndStatus {
  if (status === null || status === undefined) {
    return 'complete';
  }
  return status === 'complete' ? 'complete' : 'interrupted';
}

interface RawErrorEnvelope {
  code: string;
  message?: string | null;
  details?: Record<string, unknown> | null;
  request_id?: string | null;
}

function toErrorPayload(raw: RawErrorEnvelope): ChatStreamErrorPayload {
  return {
    code: raw.code,
    message: raw.message ?? 'The conversation failed.',
    details: raw.details ?? {},
    requestId: raw.request_id ?? null,
    // §6.1: an unregistered code resolves to `errors.internal_error` rather than leaking a raw
    // identifier into the interface.
    i18nKey: i18nKeyForErrorCode(raw.code),
  };
}

/**
 * Adapts an in-band failure to the type the shared `ErrorAlert` narrows on, so a chat failure
 * renders through the same component — and the same `common.errors.<code>` translations — as every
 * other error in the app.
 *
 * `status: 0` is deliberate and means "there was no HTTP status": the response had already
 * committed to `200` when this arrived. Inventing a plausible 500 would put a number in the logs
 * that never crossed the wire.
 */
export function chatStreamApiError(payload: ChatStreamErrorPayload): ApiError {
  return new ApiError({
    status: 0,
    code: payload.code,
    message: payload.message,
    details: payload.details,
    requestId: payload.requestId,
  });
}

function ignored(reason: IgnoredReason, eventName: string | null = null): ChatFrame {
  return { id: null, event: { type: 'ignored', reason, eventName } };
}

/**
 * Parses one complete frame — the text between two blank lines, without the separator.
 *
 * Field handling follows the SSE spec: `:`-prefixed lines are comments, an unknown field name is
 * skipped, and repeated `data:` lines join with a newline. What does *not* follow the spec is the
 * default event name: the spec says an unnamed frame is a `message` event, and this client refuses
 * that mapping because "unnamed frame" and "assistant text" are different things.
 */
export function parseChatFrame(rawFrame: string): ChatFrame {
  let eventName: string | null = null;
  let id: string | null = null;
  const dataLines: string[] = [];
  let sawComment = false;

  for (const line of rawFrame.split('\n')) {
    if (line === '') {
      continue;
    }
    if (line.startsWith(':')) {
      sawComment = true;
      continue;
    }
    const separator = line.indexOf(':');
    const field = separator === -1 ? line : line.slice(0, separator);
    // One optional leading space after the colon is part of the framing, not of the value.
    const rawValue = separator === -1 ? '' : line.slice(separator + 1);
    const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue;

    if (field === 'event') {
      eventName = value;
    } else if (field === 'id') {
      id = value;
    } else if (field === 'data') {
      dataLines.push(value);
    }
    // Any other field (`retry`, or something a future server adds) is skipped, per the spec.
  }

  if (eventName === null) {
    if (dataLines.length === 0) {
      // Comment-only frame. ADR 007's 15s `: ping` lands here, and so does a stray blank frame.
      return sawComment ? { id: null, event: { type: 'heartbeat' } } : ignored('missing_data');
    }
    return ignored('missing_event_name');
  }

  const parser = EVENT_PARSERS[eventName];
  if (parser === undefined) {
    return { id, event: { type: 'ignored', reason: 'unknown_event', eventName } };
  }
  if (dataLines.length === 0) {
    return { id, event: { type: 'ignored', reason: 'missing_data', eventName } };
  }

  let data: unknown;
  try {
    data = JSON.parse(dataLines.join('\n'));
  } catch {
    return { id, event: { type: 'ignored', reason: 'malformed_json', eventName } };
  }

  const event = parser(data);
  return event === null ? { id, event: { type: 'ignored', reason: 'invalid_payload', eventName } } : { id, event };
}

/**
 * Turns an arbitrarily chunked byte stream into whole frames.
 *
 * <b>This is the class the classic SSE bug lives in.</b> A `ReadableStream` chunk boundary has
 * nothing to do with a frame boundary: `data: {"text":"Tok` can arrive in one read and
 * `yo"}\n\n` in the next, and a parser that treats each chunk as a frame drops both halves. The
 * unterminated tail is therefore held in `buffer` until its blank line shows up.
 *
 * Stateful, so one instance belongs to one connection. After a reconnect, start a new one — the
 * old buffer holds bytes from a stream that no longer exists.
 */
export class ChatFrameDecoder {
  private buffer = '';

  /** Whole frames completed by this chunk. Returns `[]` while a frame is still arriving. */
  push(chunk: string): ChatFrame[] {
    // Normalise CRLF and lone CR before splitting: the separator is "a blank line", and a server
    // (or a proxy) using \r\n must not turn every frame into an unterminated one.
    this.buffer += chunk.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

    const frames: ChatFrame[] = [];
    let separator = this.buffer.indexOf('\n\n');
    while (separator !== -1) {
      const raw = this.buffer.slice(0, separator);
      this.buffer = this.buffer.slice(separator + 2);
      if (raw.trim() !== '') {
        frames.push(parseChatFrame(raw));
      }
      separator = this.buffer.indexOf('\n\n');
    }
    return frames;
  }

  /**
   * Called once when the body ends.
   *
   * A leftover unterminated frame is reported as `incomplete_frame` rather than parsed. The SSE
   * spec discards pending data at end of stream, and guessing here would be worse than the spec:
   * half a JSON object is not half a message, and a truncated `done` would silently mark an
   * interrupted turn as complete.
   */
  flush(): ChatFrame[] {
    const pending = this.buffer;
    this.buffer = '';
    return pending.trim() === '' ? [] : [ignored('incomplete_frame')];
  }
}
