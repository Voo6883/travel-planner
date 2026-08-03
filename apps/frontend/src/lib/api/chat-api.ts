import { z } from 'zod';
import { apiRequest, type ApiPath } from './client';

/**
 * The non-streaming half of chat: where the conversation lives, and how history is read back.
 *
 * ADR 007 scopes its codegen exception narrowly — only the *event union* is hand-authored. History
 * is an ordinary JSON endpoint, so it goes through `apiRequest` like everything else and gets the
 * same base URL, credentials, correlation id, and error translation.
 *
 * The chat paths are published in `src/generated/api/schema.d.ts` as of task 20, so the history
 * request and its response are contract-checked like every other JSON endpoint. The `as` casts
 * below are the same ones `admin-api.ts` uses and for the same reason: `apiRequest` is keyed on the
 * generated path union, and appending a query string produces a string the union does not contain.
 * The cast asserts the path, never the shape.
 */

/**
 * Which conversation a call addresses.
 *
 * Planner-level chat (PLAN §3.2) has no trip yet — it is the surface that *creates* one — so the
 * discriminant is not "a nullable tripId" but two different targets. A `null` trip id sliding into
 * a URL is exactly the bug that produces `/trips/null/chat/messages`.
 */
export type ChatTarget = { readonly scope: 'planner' } | { readonly scope: 'trip'; readonly tripId: string };

export const PLANNER_CHAT_TARGET: ChatTarget = { scope: 'planner' };

export function tripChatTarget(tripId: string): ChatTarget {
  return { scope: 'trip', tripId };
}

/**
 * The one place a chat URL is built (PLAN §3.2, ADR 007).
 *
 * `POST` streams a turn, `GET` reads history. Same path, different verb — no second path to keep
 * in step, and nothing else in the app may assemble one.
 */
export function chatMessagesPath(target: ChatTarget): string {
  return target.scope === 'planner' ? '/planner/chat/messages' : `/trips/${target.tripId}/chat/messages`;
}

/**
 * How many messages a page of history holds.
 *
 * Sent explicitly on every request, so it is this application's choice rather than the contract's:
 * the published default is 20 (`PageQuery.DEFAULT_PAGE_SIZE`, the same one `/admin/users` uses) and
 * the ceiling is 100. A chat panel is taller than an admin table and one "load older" per screenful
 * is a worse experience than one slightly larger page, so it asks for 30.
 */
export const CHAT_HISTORY_PAGE_SIZE = 30;

export type ChatMessageRole = 'user' | 'assistant' | 'system' | 'tool_call' | 'tool_result' | 'lifecycle_event';

/**
 * A persisted message, as the UI needs to think about it.
 *
 * <b>Two values, not four.</b> The backend's `ChatMessageStatus` has `STREAMING`, `COMPLETE`,
 * `INTERRUPTED` and `FAILED`, but a message read back from *history* is only ever one of two things
 * to a reader: the whole answer, or not the whole answer. A row still marked `STREAMING` when the
 * page loads is a turn whose writer went away, and `FAILED` kept whatever text arrived first — both
 * are partial, and both must be labelled rather than presented as finished.
 */
export type PersistedMessageStatus = 'complete' | 'interrupted';

export interface ChatHistoryMessage {
  readonly message_id: string;
  readonly role: ChatMessageRole;
  readonly content: string;
  readonly status: PersistedMessageStatus;
  readonly client_message_id: string | null;
  readonly created_at: string;
}

export interface ChatHistoryPage {
  readonly page: number;
  readonly page_size: number;
  readonly total: number;
  readonly conversation_id: string | null;
  readonly items: readonly ChatHistoryMessage[];
}

/**
 * Exactly the values the contract publishes — no case normalisation (STATUS F-31, now closed).
 *
 * This used to lower-case defensively, because the Java constants are `USER`/`ASSISTANT`/`SYSTEM`
 * and nothing had decided which case reached the wire. It is decided: chat DTOs serialise
 * lower-case snake through `api/dto/chat/ChatWireNames`, `ChatWireNamesTest` asserts it for every
 * constant of both enums, and `ChatMessageRole` in the contract enumerates the lower-case values.
 * Normalising here as well would mean an upper-case regression on the server passed silently on
 * this one surface and broke on every other.
 */
const roleSchema = z.union([
  z.literal('user'),
  z.literal('assistant'),
  z.literal('system'),
  z.literal('tool_call'),
  z.literal('tool_result'),
  z.literal('lifecycle_event'),
]);

const historyMessageSchema = z.object({
  message_id: z.string(),
  role: roleSchema,
  content: z.string(),
  // Any string: the mapping to "whole answer or not" happens below, where the reasoning lives.
  status: z.string().nullish(),
  client_message_id: z.string().nullish(),
  created_at: z.string(),
});

const historyPageSchema = z.object({
  page: z.number(),
  page_size: z.number(),
  total: z.number(),
  conversation_id: z.string().nullish(),
  items: z.array(historyMessageSchema),
});

export interface ChatHistoryQuery {
  readonly target: ChatTarget;
  /** Zero-based, as the rest of the API publishes it. */
  readonly page?: number;
  readonly pageSize?: number;
}

/**
 * One page of history, oldest first within the page (`seq` ascending — contractual).
 *
 * Ordering is the server's. V19's own header records that `created_at` is fixed per transaction
 * and is not an ordering key; re-sorting by timestamp (F-41) would reorder a correctly sequenced
 * turn under clock skew. Trust `seq`.
 */
export async function fetchChatHistory(query: ChatHistoryQuery, signal?: AbortSignal): Promise<ChatHistoryPage> {
  const search = new URLSearchParams({
    page: String(query.page ?? 0),
    page_size: String(query.pageSize ?? CHAT_HISTORY_PAGE_SIZE),
  });

  return apiRequest({
    path: historyPath(query.target, search),
    signal,
    validate: (payload) => parseHistoryPage(payload),
  });
}

/**
 * The generated path key for a history request.
 *
 * The cast is the one `admin-api.ts` makes for the same reason: `apiRequest` is keyed on the
 * generated union and a query string produces a value that union cannot contain. What is asserted
 * is the *path*; the response shape is still checked, by zod here and by the generated
 * `ChatHistoryPage` at the call sites that use it.
 */
function historyPath(target: ChatTarget, search: URLSearchParams): ApiPath {
  return target.scope === 'planner'
    ? (`/planner/chat/messages?${search.toString()}` as '/planner/chat/messages')
    : (`/trips/${target.tripId}/chat/messages?${search.toString()}` as '/trips/{tripId}/chat/messages');
}

function parseHistoryPage(payload: unknown): ChatHistoryPage {
  const parsed = historyPageSchema.parse(payload);
  const items = parsed.items.map((item) => ({
    message_id: item.message_id,
    role: item.role,
    content: item.content,
    status: persistedStatus(item.status),
    client_message_id: item.client_message_id ?? null,
    created_at: item.created_at,
  }));

  return {
    page: parsed.page,
    page_size: parsed.page_size,
    total: parsed.total,
    conversation_id: parsed.conversation_id ?? null,
    items,
  };
}

/**
 * Anything the server says other than `complete` is a partial answer.
 *
 * `streaming` (the writer went away mid-turn) and `failed` (an ADR 007 `StreamError` after some
 * text) both land on `interrupted`, because a reader only cares whether the answer is whole.
 *
 * <b>This collapse stays; the case normalisation that used to sit beside it is gone</b> (STATUS
 * F-31). The two were doing different jobs: four server statuses onto two reader-facing ones is a
 * real decision this layer owns, while lower-casing was a workaround for a contract that had not
 * been settled. It is settled — the wire is lower-case — so a comparison that still accepted
 * `COMPLETE` would only ever hide a regression.
 *
 * An *absent* status is treated as complete rather than as partial. The field is required in the
 * contract, so an omitted one means an older server, and labelling every reloaded user message
 * "Interrupted" would be noise rather than caution.
 */
function persistedStatus(status: string | null | undefined): PersistedMessageStatus {
  if (status === null || status === undefined) {
    return 'complete';
  }
  return status === 'complete' ? 'complete' : 'interrupted';
}
