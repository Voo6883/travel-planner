import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  chatMessagesPath,
  fetchChatHistory,
  PLANNER_CHAT_TARGET,
  tripChatTarget,
  CHAT_HISTORY_PAGE_SIZE,
} from './chat-api';

/**
 * "Durable chat messages reload in order" is a Definition-of-Done item, so ordering is asserted
 * against a deliberately shuffled payload rather than against a server that happens to be sorted.
 */

function stubHistory(body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    headers: new Headers(),
    json: async () => body,
  } as unknown as Response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function message(overrides: Record<string, unknown> = {}) {
  return {
    message_id: 'm1',
    role: 'user',
    content: 'hello',
    status: 'complete',
    client_message_id: null,
    created_at: '2026-07-01T09:00:00Z',
    ...overrides,
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('chatMessagesPath', () => {
  it('is the only place a chat URL is assembled', () => {
    expect(chatMessagesPath(PLANNER_CHAT_TARGET)).toBe('/planner/chat/messages');
    expect(chatMessagesPath(tripChatTarget('trip-9'))).toBe('/trips/trip-9/chat/messages');
  });
});

describe('fetchChatHistory', () => {
  it('requests page 0 zero-based with the published page size', async () => {
    const fetchMock = stubHistory({ page: 0, page_size: 30, total: 1, items: [message()] });

    await fetchChatHistory({ target: PLANNER_CHAT_TARGET });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`/api/v1/planner/chat/messages?page=0&page_size=${CHAT_HISTORY_PAGE_SIZE}`);
    expect(init.method).toBe('GET');
    expect(init.credentials).toBe('include');
  });

  it('returns messages oldest first even when the server does not', async () => {
    stubHistory({
      page: 0,
      page_size: 30,
      total: 3,
      items: [
        message({ message_id: 'm3', created_at: '2026-07-01T09:02:00Z' }),
        message({ message_id: 'm1', created_at: '2026-07-01T09:00:00Z' }),
        message({ message_id: 'm2', created_at: '2026-07-01T09:01:00Z' }),
      ],
    });

    const history = await fetchChatHistory({ target: tripChatTarget('trip-1'), page: 2, pageSize: 10 });

    expect(history.items.map((item) => item.message_id)).toEqual(['m1', 'm2', 'm3']);
  });

  it('keeps the server order for messages sharing a timestamp', async () => {
    // A question and the answer it triggered can land in the same millisecond. Only the server
    // knows which came first, so the sort must be stable rather than clever.
    stubHistory({
      page: 0,
      page_size: 30,
      total: 2,
      items: [
        message({ message_id: 'zzz', role: 'user', created_at: '2026-07-01T09:00:00Z' }),
        message({ message_id: 'aaa', role: 'assistant', created_at: '2026-07-01T09:00:00Z' }),
      ],
    });

    const history = await fetchChatHistory({ target: PLANNER_CHAT_TARGET });

    expect(history.items.map((item) => item.message_id)).toEqual(['zzz', 'aaa']);
  });

  it('carries the interrupted status and the client message id through', async () => {
    stubHistory({
      page: 1,
      page_size: 30,
      total: 40,
      conversation_id: 'conv-1',
      items: [
        message({ message_id: 'm9', role: 'assistant', status: 'interrupted' }),
        message({ message_id: 'm8', client_message_id: 'c8', created_at: '2026-07-01T08:59:00Z' }),
      ],
    });

    const history = await fetchChatHistory({ target: PLANNER_CHAT_TARGET, page: 1 });

    expect(history.conversation_id).toBe('conv-1');
    expect(history.items[0]?.client_message_id).toBe('c8');
    expect(history.items[1]?.status).toBe('interrupted');
  });

  it('defaults an absent status to complete rather than leaving it undefined', async () => {
    stubHistory({ page: 0, page_size: 30, total: 1, items: [{ ...message(), status: undefined }] });

    const history = await fetchChatHistory({ target: PLANNER_CHAT_TARGET });

    expect(history.items[0]?.status).toBe('complete');
  });

  it('reads every non-complete backend status as a partial answer', async () => {
    // The backend persists streaming / complete / interrupted / failed. A row still marked
    // `streaming` when the page loads is a turn whose writer went away — not a finished answer.
    stubHistory({
      page: 0,
      page_size: 30,
      total: 3,
      items: [
        message({ message_id: 'm1', role: 'assistant', status: 'complete' }),
        message({ message_id: 'm2', role: 'assistant', status: 'streaming', created_at: '2026-07-01T09:01:00Z' }),
        message({ message_id: 'm3', role: 'assistant', status: 'failed', created_at: '2026-07-01T09:02:00Z' }),
      ],
    });

    const history = await fetchChatHistory({ target: PLANNER_CHAT_TARGET });

    expect(history.items.map((item) => item.status)).toEqual(['complete', 'interrupted', 'interrupted']);
    expect(history.items[0]?.role).toBe('assistant');
  });

  it('rejects an upper-case role now that the contract publishes lower-case', async () => {
    // STATUS F-31 is settled in the DTO layer, so this page can only be upper-case if the server
    // regressed. Normalising it here would make chat the one surface where that went unnoticed.
    stubHistory({ page: 0, page_size: 30, total: 1, items: [message({ role: 'ASSISTANT' })] });

    await expect(fetchChatHistory({ target: PLANNER_CHAT_TARGET })).rejects.toThrow();
  });

  it('rejects a payload that does not match the shape rather than rendering it', async () => {
    stubHistory({ page: 0, page_size: 30, total: 1, items: [{ message_id: 'm1' }] });

    await expect(fetchChatHistory({ target: PLANNER_CHAT_TARGET })).rejects.toThrow();
  });
});
