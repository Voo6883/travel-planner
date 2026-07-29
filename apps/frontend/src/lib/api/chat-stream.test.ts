import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './api-error';
import { PLANNER_CHAT_TARGET, tripChatTarget } from './chat-api';
import type { ChatFrame } from './chat-events';
import { LAST_EVENT_ID_HEADER, SSE_ACCEPT_HEADER, streamChatMessage } from './chat-stream';

/**
 * The POST-SSE client (ADR 007). What is asserted here is everything `EventSource` could not have
 * done — a body, a CSRF header, a resume header — plus the two failure shapes the UI depends on:
 * a typed `ApiError` before the stream opens, and a plain end-of-body when it is cut.
 */

/** A body that delivers `chunks` one read at a time, so chunk boundaries are under test control. */
function streamOf(chunks: readonly string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  let index = 0;
  return new ReadableStream<Uint8Array>({
    pull(controller) {
      if (index >= chunks.length) {
        controller.close();
        return;
      }
      const chunk = chunks[index] ?? '';
      index += 1;
      controller.enqueue(encoder.encode(chunk));
    },
  });
}

function stubStream(chunks: readonly string[], status = 200) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    body: streamOf(chunks),
    json: async () => ({}),
  } as unknown as Response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function collect(request: Parameters<typeof streamChatMessage>[0]): Promise<ChatFrame[]> {
  const frames: ChatFrame[] = [];
  for await (const frame of streamChatMessage(request)) {
    frames.push(frame);
  }
  return frames;
}

const baseRequest = { target: PLANNER_CHAT_TARGET, clientMessageId: 'c1', text: 'Plan Japan' } as const;

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('streamChatMessage', () => {
  it('POSTs the message with an SSE Accept header and the session cookie', async () => {
    const fetchMock = stubStream(['event: done\ndata: {}\n\n']);

    await collect({ ...baseRequest });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/planner/chat/messages');
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>).Accept).toBe(SSE_ACCEPT_HEADER);
    // ADR 006: the session is an httpOnly cookie, which is exactly why EventSource cannot be used.
    expect(init.credentials).toBe('include');
    expect(JSON.parse(String(init.body))).toEqual({ client_message_id: 'c1', content: 'Plan Japan' });
  });

  it('addresses the trip conversation when the target is a trip', async () => {
    const fetchMock = stubStream(['event: done\ndata: {}\n\n']);

    await collect({ ...baseRequest, target: tripChatTarget('trip-9'), conversationId: 'conv-1' });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/trips/trip-9/chat/messages');
    expect(JSON.parse(String(init.body))).toMatchObject({ conversation_id: 'conv-1' });
  });

  it('echoes the CSRF cookie, because POST is a mutating method (ADR 006)', async () => {
    document.cookie = 'XSRF-TOKEN=token-from-server';
    const fetchMock = stubStream(['event: done\ndata: {}\n\n']);

    await collect({ ...baseRequest });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('token-from-server');
  });

  it('sends Last-Event-ID when resuming, and omits it otherwise', async () => {
    const resuming = stubStream(['event: done\ndata: {}\n\n']);
    await collect({ ...baseRequest, lastEventId: '41' });
    const [, resumed] = resuming.mock.calls[0] as [string, RequestInit];
    expect((resumed.headers as Record<string, string>)[LAST_EVENT_ID_HEADER]).toBe('41');

    const fresh = stubStream(['event: done\ndata: {}\n\n']);
    await collect({ ...baseRequest });
    const [, first] = fresh.mock.calls[0] as [string, RequestInit];
    expect((first.headers as Record<string, string>)[LAST_EVENT_ID_HEADER]).toBeUndefined();
  });

  it('yields frames reassembled across chunk boundaries', async () => {
    stubStream(['id: 1\nevent: text_de', 'lta\ndata: {"text":"To', 'kyo"}\n\nid: 2\nevent: done\ndata: {}\n\n']);

    const frames = await collect({ ...baseRequest });

    expect(frames.map((frame) => frame.event)).toEqual([
      { type: 'text_delta', text: 'Tokyo', messageId: null },
      { type: 'done', stopReason: null },
    ]);
    expect(frames.map((frame) => frame.id)).toEqual(['1', '2']);
  });

  it('ends without a terminal frame when the connection is cut mid-turn', async () => {
    // The caller reads this as "dropped", not "finished" — there is no `done` and no `error`.
    stubStream(['event: text_delta\ndata: {"text":"half a sen']);

    const frames = await collect({ ...baseRequest });

    expect(frames.map((frame) => frame.event.type)).toEqual(['ignored']);
  });

  it('throws a typed ApiError when the request fails before the stream opens', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 429,
        headers: new Headers({ 'X-Request-Id': 'req-3' }),
        json: async () => ({ code: 'rate_limited', message: 'too many' }),
      } as unknown as Response),
    );

    const error = await collect({ ...baseRequest }).catch((thrown: unknown) => thrown);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('rate_limited');
    expect((error as ApiError).requestId).toBe('req-3');
  });

  it('throws rather than hanging when a 200 arrives with no readable body', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, headers: new Headers(), body: null } as unknown as Response),
    );

    const error = await collect({ ...baseRequest }).catch((thrown: unknown) => thrown);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('internal_error');
  });

  it('passes the abort signal to fetch so Stop cancels the agent run server-side', async () => {
    const controller = new AbortController();
    const fetchMock = stubStream(['event: done\ndata: {}\n\n']);

    await collect({ ...baseRequest, signal: controller.signal });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.signal).toBe(controller.signal);
  });

  it('stops reading when the consumer breaks out of the loop', async () => {
    stubStream(['event: text_delta\ndata: {"text":"a"}\n\n', 'event: text_delta\ndata: {"text":"b"}\n\n']);

    const seen: string[] = [];
    for await (const frame of streamChatMessage({ ...baseRequest })) {
      seen.push(frame.event.type);
      break;
    }

    // `for await … break` runs the generator's `finally`, which releases the reader lock. A leak
    // here would keep the response body — and the model call behind it — open.
    expect(seen).toEqual(['text_delta']);
  });
});
