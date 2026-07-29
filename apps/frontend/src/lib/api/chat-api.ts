import { z } from 'zod';
import { apiRequest, type ApiPath } from './client';

/**
 * The non-streaming half of chat: where the conversation lives, and how history is read back.
 *
 * ADR 007 scopes its codegen exception narrowly — only the *event union* is hand-authored. History
 * is an ordinary JSON endpoint, so it goes through `apiRequest` like everything else and gets the
 * same base URL, credentials, correlation id, and error translation.
 *
 * <b>One unavoidable gap.</b> The chat paths are not published in `src/generated/api/schema.d.ts`
 * yet — the backend routes are being written in parallel, and ADR 007 documents the two chat paths
 * as `text/event-stream` with a description-only body. `chatPath()` below is the single place that
 * gap is expressed. When the contract publishes them, delete the cast and the compiler will check
 * every call site here for free.
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
 * Widens a chat path to the generated path union.
 *
 * Deliberately a named function rather than an inline `as`: it is the only unchecked path in the
 * app, and it should be greppable.
 */
export function chatPath(path: string): ApiPath {
  return path as ApiPath;
}

/** Matches the admin convention — the contract's published page size default. */
export const CHAT_HISTORY_PAGE_SIZE = 30;

export type ChatMessageRole = 'user' | 'assistant' | 'system';

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
 * Case-insensitive because the enum's case is not settled.
 *
 * The backend enum constants are `USER`/`ASSISTANT`/`SYSTEM`, and the existing contract publishes
 * some enums upper-case (`roles`, `linked_providers`) while ADR 007's wire names are lower-case
 * snake case. Rejecting the whole history page over capitalisation would be a blank conversation
 * for a difference that carries no meaning — so it is normalised here, once.
 */
const roleSchema = z.preprocess(
  (value) => (typeof value === 'string' ? value.toLowerCase() : value),
  z.union([z.literal('user'), z.literal('assistant'), z.literal('system')]),
);

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
 * One page of history, oldest first.
 *
 * <b>The sort is applied here, not trusted from the server.</b> "Durable chat messages reload in
 * order" is a Definition-of-Done item, and ordering that depends on a database's default row order
 * is the kind of thing that holds until an index changes.
 *
 * Ties keep the order the server sent them in — `Array.prototype.sort` is stable, and two messages
 * sharing a millisecond (a user turn and the reply it triggered can) have a real order that only
 * the server knows. Inventing one from the id would sometimes put the answer before the question.
 */
export async function fetchChatHistory(query: ChatHistoryQuery, signal?: AbortSignal): Promise<ChatHistoryPage> {
  const search = new URLSearchParams({
    page: String(query.page ?? 0),
    page_size: String(query.pageSize ?? CHAT_HISTORY_PAGE_SIZE),
  });

  return apiRequest({
    path: chatPath(`${chatMessagesPath(query.target)}?${search.toString()}`),
    signal,
    validate: (payload) => parseHistoryPage(payload),
  });
}

function parseHistoryPage(payload: unknown): ChatHistoryPage {
  const parsed = historyPageSchema.parse(payload);
  const items = parsed.items
    .map((item) => ({
      message_id: item.message_id,
      role: item.role,
      content: item.content,
      status: persistedStatus(item.status),
      client_message_id: item.client_message_id ?? null,
      created_at: item.created_at,
    }))
    .sort(compareByCreatedAt);

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
 * `STREAMING` (the writer went away mid-turn) and `FAILED` (an ADR 007 `StreamError` after some
 * text) both land on `interrupted`, because a reader only cares whether the answer is whole. The
 * comparison is case-insensitive for the reason given on `roleSchema`.
 *
 * An *absent* status is treated as complete rather than as partial. The column is non-null on the
 * backend, so an omitted field means the DTO does not carry one — which is the shape a user message
 * takes — and labelling every reloaded user message "Interrupted" would be noise, not caution.
 */
function persistedStatus(status: string | null | undefined): PersistedMessageStatus {
  if (status === null || status === undefined) {
    return 'complete';
  }
  return status.toLowerCase() === 'complete' ? 'complete' : 'interrupted';
}

function compareByCreatedAt(left: ChatHistoryMessage, right: ChatHistoryMessage): number {
  if (left.created_at === right.created_at) {
    return 0;
  }
  return left.created_at < right.created_at ? -1 : 1;
}
