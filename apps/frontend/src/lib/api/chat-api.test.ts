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
    seq: 1,
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
        message({ message_id: 'm3', seq: 3 }),
        message({ message_id: 'm1', seq: 1 }),
        message({ message_id: 'm2', seq: 2 }),
      ],
    });

    const history = await fetchChatHistory({ target: tripChatTarget('trip-1'), page: 2, pageSize: 10 });

    expect(history.items.map((item) => item.message_id)).toEqual(['m1', 'm2', 'm3']);
  });

  it('orders a turn correctly even though its rows share one timestamp', async () => {
    // The regression. Postgres fixes now() for a whole transaction, so a turn's rows are stamped
    // identically — the old comparator returned 0 for all of them and the order was whatever the
    // response happened to contain. seq is the server's real order and disagrees with it here.
    stubHistory({
      page: 0,
      page_size: 30,
      total: 3,
      items: [
        message({ message_id: 'reply', seq: 12, role: 'assistant', created_at: '2026-07-01T09:00:00Z' }),
        message({ message_id: 'question', seq: 10, role: 'user', created_at: '2026-07-01T09:00:00Z' }),
        message({ message_id: 'lookup', seq: 11, role: 'tool_call', created_at: '2026-07-01T09:00:00Z' }),
      ],
    });

    const history = await fetchChatHistory({ target: PLANNER_CHAT_TARGET });

    expect(history.items.map((item) => item.message_id)).toEqual(['question', 'lookup', 'reply']);
  });

  it('parses every role the contract publishes rather than failing the whole page', async () => {
    // The bug this replaced: roleSchema listed three of the six published roles, so one tool_call
    // row anywhere in a page failed the array parse and the reader lost the entire conversation.
    stubHistory({
      page: 0,
      page_size: 30,
      total: 6,
      items: [
        message({ message_id: 'a', seq: 1, role: 'user' }),
        message({ message_id: 'b', seq: 2, role: 'assistant' }),
        message({ message_id: 'c', seq: 3, role: 'system' }),
        message({ message_id: 'd', seq: 4, role: 'tool_call' }),
        message({ message_id: 'e', seq: 5, role: 'tool_result' }),
        message({ message_id: 'f', seq: 6, role: 'lifecycle_event' }),
      ],
    });

    const history = await fetchChatHistory({ target: PLANNER_CHAT_TARGET });

    expect(history.items).toHaveLength(6);
    expect(history.items.map((item) => item.role)).toEqual([
      'user',
      'assistant',
      'system',
      'tool_call',
      'tool_result',
      'lifecycle_event',
    ]);
  });

  it('carries the interrupted status and the client message id through', async () => {
    stubHistory({
      page: 1,
      page_size: 30,
      total: 40,
      conversation_id: 'conv-1',
      items: [
        message({ message_id: 'm9', seq: 9, role: 'assistant', status: 'interrupted' }),
        message({ message_id: 'm8', seq: 8, client_message_id: 'c8' }),
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
