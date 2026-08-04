import { describe, expect, it } from 'vitest';
import { ChatFrameDecoder, chatStreamApiError, parseChatFrame, type ChatFrame } from './chat-events';

/**
 * The event union is hand-authored (ADR 007), so these tests are the only thing standing between a
 * wire-format change and a silently mis-parsed conversation. Two properties matter more than the
 * happy path:
 *
 * - a frame split across chunk boundaries is reassembled, not dropped;
 * - anything unrecognised becomes an explicit `ignored`, never text and never a thrown error.
 */

function frame(event: string, data: unknown, id?: number): string {
  const idLine = id === undefined ? '' : `id: ${id}\n`;
  return `${idLine}event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
}

/** Feeds a whole stream through the decoder in one chunk. */
function decodeAll(text: string): ChatFrame[] {
  const decoder = new ChatFrameDecoder();
  return [...decoder.push(text), ...decoder.flush()];
}

describe('parseChatFrame — every event the contract defines', () => {
  it('parses a text delta', () => {
    const parsed = parseChatFrame('id: 42\nevent: text_delta\ndata: {"text":"Tokyo in spring","message_id":"m1"}');

    expect(parsed).toEqual({
      id: '42',
      event: { type: 'text_delta', text: 'Tokyo in spring', messageId: 'm1' },
    });
  });

  it('parses a message start carrying the client message id used for de-duplication', () => {
    const parsed = parseChatFrame(
      'event: message_start\ndata: {"message_id":"m1","role":"user","client_message_id":"c1","content":"hi"}',
    );

    expect(parsed.event).toEqual({
      type: 'message_start',
      messageId: 'm1',
      role: 'user',
      clientMessageId: 'c1',
      content: 'hi',
      createdAt: null,
    });
  });

  it('defaults a message end with no status to complete', () => {
    const parsed = parseChatFrame('event: message_end\ndata: {"message_id":"m1"}');

    expect(parsed.event).toEqual({ type: 'message_end', messageId: 'm1', status: 'complete' });
  });

  it('parses an interrupted message end (ADR 007 partial turn)', () => {
    const parsed = parseChatFrame('event: message_end\ndata: {"message_id":"m1","status":"interrupted"}');

    expect(parsed.event).toEqual({ type: 'message_end', messageId: 'm1', status: 'interrupted' });
  });

  it('collapses every non-complete backend status onto interrupted', () => {
    // The backend enum has four constants; a reader only needs to know whether the answer is whole.
    // The collapse survived F-31 — it is a real decision. The case normalisation next to it did not.
    const statuses = ['interrupted', 'failed', 'streaming'];

    for (const status of statuses) {
      const parsed = parseChatFrame(`event: message_end\ndata: {"message_id":"m1","status":"${status}"}`);
      expect(parsed.event).toEqual({ type: 'message_end', messageId: 'm1', status: 'interrupted' });
    }

    const complete = parseChatFrame('event: message_end\ndata: {"message_id":"m1","status":"complete"}');
    expect(complete.event).toEqual({ type: 'message_end', messageId: 'm1', status: 'complete' });
  });

  it('reads the lower-case roles the contract publishes', () => {
    // STATUS F-31 is settled: chat DTOs serialise lower-case snake and `ChatWireNamesTest` asserts
    // it for every constant of both enums, so this parser no longer normalises case.
    for (const role of ['user', 'assistant', 'system']) {
      const parsed = parseChatFrame(`event: message_start\ndata: {"message_id":"m1","role":"${role}"}`);
      expect(parsed.event).toMatchObject({ type: 'message_start', role });
    }
  });

  it('does not silently accept an upper-case role now that the contract is settled', () => {
    // An upper-case role would mean the server regressed. Rendering it anyway would hide that here
    // while every other chat surface broke — so the frame becomes an explicit `ignored`, never text.
    const parsed = parseChatFrame('event: message_start\ndata: {"message_id":"m1","role":"ASSISTANT"}');

    expect(parsed.event).toEqual({ type: 'ignored', reason: 'invalid_payload', eventName: 'message_start' });
  });

  it('parses the whole tool lifecycle', () => {
    const events = decodeAll(
      frame('tool_use_start', { tool_call_id: 't1', name: 'create_trip' }) +
        frame('tool_input_delta', { tool_call_id: 't1', json_chunk: '{"dest":' }) +
        frame('tool_use_end', { tool_call_id: 't1' }) +
        frame('tool_result', { tool_call_id: 't1', payload: { trip_id: 'trip-1' } }),
    ).map((decoded) => decoded.event);

    expect(events).toEqual([
      { type: 'tool_use_start', toolCallId: 't1', name: 'create_trip' },
      { type: 'tool_input_delta', toolCallId: 't1', jsonChunk: '{"dest":' },
      { type: 'tool_use_end', toolCallId: 't1' },
      { type: 'tool_result', toolCallId: 't1', payload: { trip_id: 'trip-1' } },
    ]);
  });

  it('parses trip_created as its own frame rather than out of prose (PLAN §3.2)', () => {
    const parsed = parseChatFrame('event: trip_created\ndata: {"trip_id":"trip-9"}');

    expect(parsed.event).toEqual({ type: 'trip_created', tripId: 'trip-9' });
  });

  it('parses brief_updated as its own frame carrying the trip id (task 22, UC-C5-09)', () => {
    const parsed = parseChatFrame('event: brief_updated\ndata: {"trip_id":"trip-7"}');

    expect(parsed.event).toEqual({ type: 'brief_updated', tripId: 'trip-7' });
  });

  it('ignores a brief_updated frame missing its trip id rather than treating it as text', () => {
    const parsed = parseChatFrame('event: brief_updated\ndata: {}');

    expect(parsed.event).toEqual({ type: 'ignored', reason: 'invalid_payload', eventName: 'brief_updated' });
  });

  it('parses usage and completion', () => {
    const usage = parseChatFrame('event: usage\ndata: {"input_tokens":10,"output_tokens":20}');
    const done = parseChatFrame('event: done\ndata: {"stop_reason":"end_turn"}');

    expect(usage.event).toEqual({ type: 'usage', inputTokens: 10, outputTokens: 20, cachedTokens: null });
    expect(done.event).toEqual({ type: 'done', stopReason: 'end_turn' });
  });

  it('parses a terminal error into the §6.1 envelope with a translation key', () => {
    const parsed = parseChatFrame(
      'event: error\ndata: {"code":"ai_timeout","message":"provider timed out","request_id":"req-7"}',
    );

    expect(parsed.event).toEqual({
      type: 'error',
      error: {
        code: 'ai_timeout',
        message: 'provider timed out',
        details: {},
        requestId: 'req-7',
        i18nKey: 'errors.ai_timeout',
      },
    });
  });

  it('falls back to internal_error for an unregistered code rather than rendering a raw key', () => {
    const parsed = parseChatFrame('event: error\ndata: {"code":"brand_new_code","message":"nope"}');

    expect(parsed.event).toEqual(
      expect.objectContaining({ type: 'error', error: expect.objectContaining({ i18nKey: 'errors.internal_error' }) }),
    );
  });

  it('reads a heartbeat comment as liveness, not as data', () => {
    expect(parseChatFrame(': ping').event).toEqual({ type: 'heartbeat' });
  });

  it('joins repeated data lines with a newline, per the SSE spec', () => {
    // A server that pretty-prints its payload sends one `data:` line per line of JSON. Joining
    // with anything other than a newline — or taking only the last line — corrupts the object.
    const parsed = parseChatFrame('event: text_delta\ndata: {\ndata:   "text": "one two"\ndata: }');

    expect(parsed.event).toEqual({ type: 'text_delta', text: 'one two', messageId: null });
  });

  it('tolerates a data line with no space after the colon', () => {
    expect(parseChatFrame('event:done\ndata:{"stop_reason":null}').event).toEqual({ type: 'done', stopReason: null });
  });
});

describe('parseChatFrame — everything that is not a valid frame', () => {
  it('ignores an event name this client does not implement, and says which', () => {
    const parsed = parseChatFrame('id: 8\nevent: reasoning_delta\ndata: {"text":"thinking"}');

    // The whole point: a future or internal event must never be rendered as assistant text.
    expect(parsed).toEqual({
      id: '8',
      event: { type: 'ignored', reason: 'unknown_event', eventName: 'reasoning_delta' },
    });
  });

  it('ignores malformed JSON without throwing', () => {
    const parsed = parseChatFrame('event: text_delta\ndata: {"text":');

    expect(parsed.event).toEqual({ type: 'ignored', reason: 'malformed_json', eventName: 'text_delta' });
  });

  it('ignores JSON of the wrong shape', () => {
    const parsed = parseChatFrame('event: text_delta\ndata: {"text":42}');

    expect(parsed.event).toEqual({ type: 'ignored', reason: 'invalid_payload', eventName: 'text_delta' });
  });

  it('refuses to treat an unnamed frame as a message event', () => {
    const parsed = parseChatFrame('data: {"text":"sneaky"}');

    expect(parsed.event).toEqual({ type: 'ignored', reason: 'missing_event_name', eventName: null });
  });

  it('ignores a named frame with no data line', () => {
    const parsed = parseChatFrame('event: done');

    expect(parsed.event).toEqual({ type: 'ignored', reason: 'missing_data', eventName: 'done' });
  });

  it('ignores an empty frame that is not even a comment', () => {
    expect(parseChatFrame('retry: 3000').event).toEqual({ type: 'ignored', reason: 'missing_data', eventName: null });
  });
});

describe('ChatFrameDecoder — chunk boundaries', () => {
  it('reassembles a frame split in the middle of its JSON', () => {
    // The classic SSE bug. A ReadableStream chunk boundary has nothing to do with a frame boundary.
    const decoder = new ChatFrameDecoder();

    expect(decoder.push('event: text_delta\ndata: {"text":"Tok')).toEqual([]);
    expect(decoder.push('yo"}\n\n').map((decoded) => decoded.event)).toEqual([
      { type: 'text_delta', text: 'Tokyo', messageId: null },
    ]);
  });

  it('reassembles a frame split inside the event name', () => {
    const decoder = new ChatFrameDecoder();

    expect(decoder.push('event: text_de')).toEqual([]);
    expect(decoder.push('lta\ndata: {"text":"hi"}\n\n')).toHaveLength(1);
  });

  it('reassembles a frame whose separator itself is split across two chunks', () => {
    const decoder = new ChatFrameDecoder();

    expect(decoder.push('event: done\ndata: {}\n')).toEqual([]);
    expect(decoder.push('\n').map((decoded) => decoded.event)).toEqual([{ type: 'done', stopReason: null }]);
  });

  it('emits several frames delivered in one chunk, in order', () => {
    const events = decodeAll(
      frame('message_start', { message_id: 'm1', role: 'assistant' }, 1) +
        frame('text_delta', { text: 'a' }, 2) +
        frame('text_delta', { text: 'b' }, 3) +
        frame('done', { stop_reason: 'end_turn' }, 4),
    );

    expect(events.map((decoded) => decoded.event.type)).toEqual(['message_start', 'text_delta', 'text_delta', 'done']);
    expect(events.map((decoded) => decoded.id)).toEqual(['1', '2', '3', '4']);
  });

  it('handles CRLF framing from a proxy that rewrites line endings', () => {
    const events = decodeAll('event: text_delta\r\ndata: {"text":"hi"}\r\n\r\n');

    expect(events.map((decoded) => decoded.event)).toEqual([{ type: 'text_delta', text: 'hi', messageId: null }]);
  });

  it('passes heartbeats through between real frames', () => {
    const events = decodeAll(`: ping\n\n${frame('text_delta', { text: 'hi' })}: ping\n\n`);

    expect(events.map((decoded) => decoded.event.type)).toEqual(['heartbeat', 'text_delta', 'heartbeat']);
  });

  it('reports a truncated trailing frame rather than guessing at it', () => {
    const decoder = new ChatFrameDecoder();
    decoder.push('event: done\ndata: {"stop_re');

    // Half a `done` frame must not be read as a completed turn — that is the difference between
    // "finished" and "the connection died", and the reducer treats them very differently.
    expect(decoder.flush().map((decoded) => decoded.event)).toEqual([
      { type: 'ignored', reason: 'incomplete_frame', eventName: null },
    ]);
  });

  it('flushes nothing when the stream ended on a frame boundary', () => {
    const decoder = new ChatFrameDecoder();
    decoder.push(frame('done', {}));

    expect(decoder.flush()).toEqual([]);
  });
});

describe('chatStreamApiError', () => {
  it('adapts an in-band failure to the shared ApiError so one alert renders every error', () => {
    const parsed = parseChatFrame(
      'event: error\ndata: {"code":"rate_limited","message":"slow down","details":{"a":1}}',
    );
    if (parsed.event.type !== 'error') {
      throw new Error('expected an error event');
    }

    const error = chatStreamApiError(parsed.event.error);

    // status 0 = "there was no HTTP status": the response had already committed to 200.
    expect(error.status).toBe(0);
    expect(error.code).toBe('rate_limited');
    expect(error.i18nKey).toBe('errors.rate_limited');
    expect(error.details).toEqual({ a: 1 });
  });
});
