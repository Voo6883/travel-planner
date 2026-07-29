import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PLANNER_CHAT_TARGET } from '@/lib/api/chat-api';
import { useChatStream } from './use-chat-stream';

/**
 * The hook is the only place where cancellation, reconnect and idempotency meet. These tests drive
 * it through a stubbed `fetch` so the awkward orderings — a body that stops mid-sentence, a Stop
 * press, a retry of a message the server may already hold — are reproducible.
 */

type StreamBody = (init: RequestInit) => ReadableStream<Uint8Array>;

/**
 * A body that emits `chunks` and then either closes or hangs until aborted. Hanging is what a real
 * open stream does between tokens, and it is the only way to test Stop.
 */
function bodyOf(chunks: readonly string[], hang: boolean): StreamBody {
  return (init) => {
    const signal = init.signal;
    const encoder = new TextEncoder();
    let index = 0;
    return new ReadableStream<Uint8Array>({
      pull(controller) {
        if (index < chunks.length) {
          const chunk = chunks[index] ?? '';
          index += 1;
          controller.enqueue(encoder.encode(chunk));
          return undefined;
        }
        if (!hang) {
          controller.close();
          return undefined;
        }
        return new Promise<void>((_resolve, reject) => {
          signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
        });
      },
    });
  };
}

interface ChatStub {
  readonly posts: RequestInit[];
  readonly gets: string[];
}

function stubChat(turns: readonly StreamBody[], history: readonly unknown[] = []): ChatStub {
  const posts: RequestInit[] = [];
  const gets: string[] = [];
  const remaining = [...turns];

  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init: RequestInit = {}) => {
      if ((init.method ?? 'GET') === 'GET') {
        gets.push(url);
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers(),
          json: async () => ({ page: 0, page_size: 30, total: history.length, items: history }),
        } as unknown as Response);
      }
      posts.push(init);
      const body = remaining.shift() ?? bodyOf(['event: done\ndata: {}\n\n'], false);
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers(),
        body: body(init),
      } as unknown as Response);
    }),
  );

  return { posts, gets };
}

function options(overrides: Record<string, unknown> = {}) {
  return { target: PLANNER_CHAT_TARGET, maxReconnectAttempts: 0, reconnectDelayMs: 0, ...overrides };
}

function bodyOfPost(init: RequestInit | undefined): Record<string, unknown> {
  return JSON.parse(String(init?.body)) as Record<string, unknown>;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('useChatStream', () => {
  it('loads history on mount, oldest first', async () => {
    const stub = stubChat(
      [],
      [
        {
          message_id: 'm1',
          role: 'user',
          content: 'hello',
          status: 'complete',
          client_message_id: null,
          created_at: '2026-07-01T09:00:00Z',
        },
      ],
    );

    const { result } = renderHook(() => useChatStream(options()));

    await waitFor(() => expect(result.current.messages).toHaveLength(1));
    expect(stub.gets[0]).toContain('/planner/chat/messages?page=0');
    expect(result.current.messages[0]?.text).toBe('hello');
  });

  it('streams a turn and completes it', async () => {
    stubChat([
      bodyOf(
        [
          'id: 1\nevent: message_start\ndata: {"message_id":"a1","role":"assistant"}\n\n',
          'id: 2\nevent: text_delta\ndata: {"text":"Tokyo in spring"}\n\n',
          'id: 3\nevent: done\ndata: {"stop_reason":"end_turn"}\n\n',
        ],
        false,
      ),
    ]);

    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false })));

    act(() => result.current.send('Plan Japan'));

    await waitFor(() => expect(result.current.isStreaming).toBe(false));
    expect(result.current.messages.map((message) => message.text)).toEqual(['Plan Japan', 'Tokyo in spring']);
    expect(result.current.messages[1]?.status).toBe('complete');
  });

  it('sends a client message id and reconciles the echo into one bubble', async () => {
    // The server echoes back whatever id the client minted — that is the whole reconciliation
    // mechanism, so the stub reads it off the request rather than being told it in advance.
    const echoTurn: StreamBody = (init) => {
      const clientMessageId = bodyOfPost(init).client_message_id;
      const start = { message_id: 'm1', role: 'user', client_message_id: clientMessageId, content: 'Plan Japan' };
      return bodyOf(
        [`event: message_start\ndata: ${JSON.stringify(start)}\n\n`, 'event: done\ndata: {}\n\n'],
        false,
      )(init);
    };
    const stub = stubChat([echoTurn]);

    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false })));
    act(() => result.current.send('Plan Japan'));
    await waitFor(() => expect(stub.posts).toHaveLength(1));

    expect(typeof bodyOfPost(stub.posts[0]).client_message_id).toBe('string');
    await waitFor(() => expect(result.current.isStreaming).toBe(false));
    expect(result.current.messages).toHaveLength(1);
    expect(result.current.messages[0]).toMatchObject({ messageId: 'm1', status: 'sent' });
  });

  it('retries on the original client message id, so the server can reject the duplicate', async () => {
    // The DoD item, end to end: two requests, one message id, one bubble.
    const stub = stubChat([bodyOf([], true)]);
    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false })));

    act(() => result.current.send('Plan Japan'));
    await waitFor(() => expect(stub.posts).toHaveLength(1));
    act(() => result.current.stop());
    await waitFor(() => expect(result.current.isStreaming).toBe(false));

    act(() => result.current.retry());
    await waitFor(() => expect(stub.posts).toHaveLength(2));
    await waitFor(() => expect(result.current.isStreaming).toBe(false));

    expect(bodyOfPost(stub.posts[1]).client_message_id).toBe(bodyOfPost(stub.posts[0]).client_message_id);
    expect(result.current.messages.filter((message) => message.role === 'user')).toHaveLength(1);
  });

  it('marks the partial answer stopped when the user presses Stop, keeping what arrived', async () => {
    stubChat([
      bodyOf(
        [
          'event: message_start\ndata: {"message_id":"a1","role":"assistant"}\n\n',
          'event: text_delta\ndata: {"text":"Tokyo in spr"}\n\n',
        ],
        true,
      ),
    ]);

    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false })));
    act(() => result.current.send('Plan Japan'));
    await waitFor(() => expect(result.current.messages).toHaveLength(2));

    act(() => result.current.stop());

    await waitFor(() => expect(result.current.messages[1]?.status).toBe('stopped'));
    expect(result.current.messages[1]?.text).toBe('Tokyo in spr');
    expect(result.current.isStreaming).toBe(false);
  });

  it('marks the turn interrupted when the body ends without done or error', async () => {
    // ADR 007: a terminal failure is always an `error` frame. A body that merely stops was cut.
    stubChat([
      bodyOf(
        [
          'event: message_start\ndata: {"message_id":"a1","role":"assistant"}\n\n',
          'event: text_delta\ndata: {"text":"Tokyo in spr"}\n\n',
        ],
        false,
      ),
    ]);

    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false })));
    act(() => result.current.send('Plan Japan'));

    await waitFor(() => expect(result.current.messages[1]?.status).toBe('interrupted'));
    expect(result.current.messages[1]?.text).toBe('Tokyo in spr');
  });

  it('reconnects with Last-Event-ID and continues the same answer', async () => {
    const stub = stubChat([
      bodyOf(
        [
          'id: 1\nevent: message_start\ndata: {"message_id":"a1","role":"assistant"}\n\n',
          'id: 2\nevent: text_delta\ndata: {"text":"Tokyo in spr"}\n\n',
        ],
        false,
      ),
      bodyOf(['id: 3\nevent: text_delta\ndata: {"text":"ing"}\n\n', 'id: 4\nevent: done\ndata: {}\n\n'], false),
    ]);

    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false, maxReconnectAttempts: 1 })));
    act(() => result.current.send('Plan Japan'));

    await waitFor(() => expect(stub.posts).toHaveLength(2));
    await waitFor(() => expect(result.current.isStreaming).toBe(false));

    expect((stub.posts[1]?.headers as Record<string, string>)['Last-Event-ID']).toBe('2');
    expect(result.current.messages[1]).toMatchObject({ text: 'Tokyo in spring', status: 'complete' });
  });

  it('surfaces a terminal error frame and stops rather than reconnecting', async () => {
    const stub = stubChat([
      bodyOf(['event: error\ndata: {"code":"ai_timeout","message":"timed out","request_id":"req-7"}\n\n'], false),
    ]);

    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false, maxReconnectAttempts: 2 })));
    act(() => result.current.send('Plan Japan'));

    await waitFor(() => expect(result.current.error?.i18nKey).toBe('errors.ai_timeout'));
    expect(result.current.error?.requestId).toBe('req-7');
    // A typed error is the server's final word; retrying it automatically would just repeat it.
    expect(stub.posts).toHaveLength(1);
  });

  it('reports a rejected request as a typed error and marks the message not sent', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 429,
        headers: new Headers(),
        json: async () => ({ code: 'rate_limited', message: 'slow down' }),
      } as unknown as Response),
    );

    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false })));
    act(() => result.current.send('Plan Japan'));

    await waitFor(() => expect(result.current.error?.i18nKey).toBe('errors.rate_limited'));
    expect(result.current.messages[0]?.status).toBe('failed');
  });

  it('translates a network failure into the standard envelope rather than a raw TypeError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init: RequestInit = {}) =>
        (init.method ?? 'GET') === 'GET'
          ? Promise.reject(new TypeError('Failed to fetch'))
          : Promise.reject(new TypeError('Failed to fetch')),
      ),
    );

    const { result } = renderHook(() => useChatStream(options()));

    await waitFor(() => expect(result.current.error?.code).toBe('internal_error'));
    expect(result.current.error?.message).not.toContain('Failed to fetch');
  });

  it('ignores an empty draft and a send while a turn is already in flight', async () => {
    const stub = stubChat([bodyOf([], true)]);
    const { result } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false })));

    act(() => result.current.send('   '));
    expect(stub.posts).toHaveLength(0);

    act(() => result.current.send('Plan Japan'));
    await waitFor(() => expect(result.current.isStreaming).toBe(true));
    act(() => result.current.send('And also Korea'));

    expect(stub.posts).toHaveLength(1);
    act(() => result.current.stop());
    await waitFor(() => expect(result.current.isStreaming).toBe(false));
  });

  it('pages backwards through history only while there is more to load', async () => {
    const page = Array.from({ length: 30 }, (_unused, index) => ({
      message_id: `m${index}`,
      role: 'user',
      content: `message ${index}`,
      status: 'complete',
      client_message_id: null,
      created_at: `2026-07-01T09:${String(index).padStart(2, '0')}:00Z`,
    }));

    const posts: RequestInit[] = [];
    const gets: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init: RequestInit = {}) => {
        if ((init.method ?? 'GET') === 'GET') {
          gets.push(url);
          return Promise.resolve({
            ok: true,
            status: 200,
            headers: new Headers(),
            json: async () => ({ page: gets.length - 1, page_size: 30, total: 45, items: page }),
          } as unknown as Response);
        }
        posts.push(init);
        return Promise.reject(new Error('no turn expected'));
      }),
    );

    const { result } = renderHook(() => useChatStream(options()));

    await waitFor(() => expect(result.current.hasMoreHistory).toBe(true));
    act(() => result.current.loadOlderMessages());
    await waitFor(() => expect(gets).toHaveLength(2));

    expect(gets[1]).toContain('page=1');
    // 2 × 30 covers a total of 45, so there is nothing left to page to.
    await waitFor(() => expect(result.current.hasMoreHistory).toBe(false));
  });

  it('aborts the turn when the component unmounts, so the agent run is cancelled', async () => {
    let aborted = false;
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init: RequestInit = {}) => {
        init.signal?.addEventListener('abort', () => {
          aborted = true;
        });
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers(),
          body: bodyOf([], true)(init),
        } as unknown as Response);
      }),
    );

    const { result, unmount } = renderHook(() => useChatStream(options({ loadHistoryOnMount: false })));
    act(() => result.current.send('Plan Japan'));
    await waitFor(() => expect(result.current.isStreaming).toBe(true));

    unmount();

    expect(aborted).toBe(true);
  });
});
