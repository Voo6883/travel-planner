import { describe, expect, it } from 'vitest';
import type { ChatHistoryMessage } from '@/lib/api/chat-api';
import type { ChatFrame, ChatStreamEvent } from '@/lib/api/chat-events';
import { chatReducer, initialChatState, isStreaming, type ChatAction, type ChatState } from './chat-state';

/**
 * The reducer is where "a partial answer is never presented as a whole answer" is actually
 * enforced, and where a retry either does or does not duplicate a user's message. Both are stated
 * in task 20's Definition of Done, so both are asserted as data here rather than through the DOM.
 */

function frame(event: ChatStreamEvent, id?: string): ChatAction {
  return { type: 'frame', frame: { id: id ?? null, event } satisfies ChatFrame };
}

function apply(actions: readonly ChatAction[], from: ChatState = initialChatState): ChatState {
  return actions.reduce(chatReducer, from);
}

const send = (clientMessageId: string, text: string): ChatAction => ({
  type: 'user_message_sent',
  clientMessageId,
  text,
  createdAt: '2026-07-01T09:00:00Z',
});

const persisted = (overrides: Partial<ChatHistoryMessage> = {}): ChatHistoryMessage => ({
  message_id: 'm1',
  seq: 1,
  role: 'user',
  content: 'hello',
  status: 'complete',
  client_message_id: null,
  created_at: '2026-07-01T09:00:00Z',
  ...overrides,
});

describe('optimistic sends and de-duplication', () => {
  it('shows the user message immediately, before the server has seen it', () => {
    const state = apply([send('c1', 'Plan Japan')]);

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]).toMatchObject({ role: 'user', text: 'Plan Japan', status: 'pending' });
  });

  it('renders one message when the same client id is sent twice', () => {
    // The DoD item. A retry reuses the client message id (§7.3), and a second bubble would tell a
    // user they had said the same thing twice.
    const state = apply([send('c1', 'Plan Japan'), send('c1', 'Plan Japan')]);

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]?.status).toBe('pending');
  });

  it('reconciles the server echo onto the optimistic bubble instead of appending a copy', () => {
    const state = apply([
      send('c1', 'Plan Japan'),
      frame({
        type: 'message_start',
        messageId: 'm1',
        role: 'user',
        clientMessageId: 'c1',
        content: 'Plan Japan',
        createdAt: '2026-07-01T09:00:01Z',
      }),
    ]);

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]).toMatchObject({ messageId: 'm1', clientMessageId: 'c1', status: 'sent' });
  });

  it('keeps the React key stable when the server id arrives, so the bubble is not remounted', () => {
    const optimistic = apply([send('c1', 'Plan Japan')]);
    const reconciled = chatReducer(
      optimistic,
      frame({
        type: 'message_start',
        messageId: 'm1',
        role: 'user',
        clientMessageId: 'c1',
        content: null,
        createdAt: null,
      }),
    );

    expect(reconciled.messages[0]?.key).toBe(optimistic.messages[0]?.key);
  });

  it('prefers the persisted text over the optimistic text', () => {
    const state = apply([
      send('c1', '  Plan Japan  '),
      frame({
        type: 'message_start',
        messageId: 'm1',
        role: 'user',
        clientMessageId: 'c1',
        content: 'Plan Japan',
        createdAt: null,
      }),
    ]);

    expect(state.messages[0]?.text).toBe('Plan Japan');
  });

  it('appends an echo whose client id it has never seen — another tab, or a reload', () => {
    const state = apply([
      frame({
        type: 'message_start',
        messageId: 'm1',
        role: 'user',
        clientMessageId: 'c-elsewhere',
        content: 'from my phone',
        createdAt: null,
      }),
    ]);

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]).toMatchObject({ status: 'sent', text: 'from my phone' });
  });
});

describe('streaming an assistant turn', () => {
  const opened = frame({
    type: 'message_start',
    messageId: 'a1',
    role: 'assistant',
    clientMessageId: null,
    content: null,
    createdAt: null,
  });

  it('accumulates deltas into one message rather than one bubble per token', () => {
    const state = apply([
      { type: 'stream_opened' },
      opened,
      frame({ type: 'text_delta', messageId: 'a1', text: 'Tokyo ' }),
      frame({ type: 'text_delta', messageId: 'a1', text: 'in spring' }),
    ]);

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]).toMatchObject({ text: 'Tokyo in spring', status: 'streaming' });
  });

  it('routes a delta with no message id to the open turn', () => {
    const state = apply([
      { type: 'stream_opened' },
      opened,
      frame({ type: 'text_delta', messageId: null, text: 'Tokyo' }),
    ]);

    expect(state.messages[0]?.text).toBe('Tokyo');
  });

  it('opens a turn for a delta that arrives without a message_start', () => {
    const state = apply([{ type: 'stream_opened' }, frame({ type: 'text_delta', messageId: null, text: 'Tokyo' })]);

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0]).toMatchObject({ role: 'assistant', text: 'Tokyo', status: 'streaming' });
  });

  it('finalises the turn on message_end', () => {
    const state = apply([
      { type: 'stream_opened' },
      opened,
      frame({ type: 'text_delta', messageId: 'a1', text: 'Tokyo' }),
      frame({ type: 'message_end', messageId: 'a1', status: 'complete' }),
    ]);

    expect(state.messages[0]?.status).toBe('complete');
  });

  it('closes the connection and completes an open turn on done', () => {
    const state = apply([
      { type: 'stream_opened' },
      opened,
      frame({ type: 'text_delta', messageId: 'a1', text: 'Tokyo' }),
      frame({ type: 'done', stopReason: 'end_turn' }),
    ]);

    expect(state.connection).toBe('closed');
    expect(state.messages[0]?.status).toBe('complete');
    expect(isStreaming(state)).toBe(false);
  });

  it('tracks tool activity without keeping the arguments', () => {
    const running = apply([
      { type: 'stream_opened' },
      frame({ type: 'tool_use_start', toolCallId: 't1', name: 'create_trip' }),
      frame({ type: 'tool_input_delta', toolCallId: 't1', jsonChunk: '{"destination":"Japan"}' }),
    ]);

    expect(running.tools).toEqual([{ toolCallId: 't1', name: 'create_trip', running: true }]);
    // §7.2 forbids showing raw tool JSON. The safest guarantee is to hold none of it.
    expect(JSON.stringify(running)).not.toContain('destination');

    const finished = chatReducer(running, frame({ type: 'tool_result', toolCallId: 't1', payload: { ok: true } }));
    expect(finished.tools[0]?.running).toBe(false);
  });

  it('records trip_created for the route layer to act on', () => {
    const state = apply([frame({ type: 'trip_created', tripId: 'trip-9' })]);

    expect(state.tripCreatedId).toBe('trip-9');
  });

  it('ignores heartbeats, usage and unparsed frames without disturbing the conversation', () => {
    const before = apply([
      { type: 'stream_opened' },
      opened,
      frame({ type: 'text_delta', messageId: 'a1', text: 'x' }),
    ]);

    const after = apply(
      [
        frame({ type: 'heartbeat' }),
        frame({ type: 'usage', inputTokens: 5, outputTokens: 9, cachedTokens: null }),
        frame({ type: 'ignored', reason: 'unknown_event', eventName: 'reasoning_delta' }),
      ],
      before,
    );

    expect(after.messages).toEqual(before.messages);
    expect(after.connection).toBe(before.connection);
  });
});

describe('interruption, cancellation and failure', () => {
  const openTurn: readonly ChatAction[] = [
    { type: 'stream_opened' },
    frame({
      type: 'message_start',
      messageId: 'a1',
      role: 'assistant',
      clientMessageId: null,
      content: null,
      createdAt: null,
    }),
    frame({ type: 'text_delta', messageId: 'a1', text: 'Tokyo in spr' }),
  ];

  it('marks a partial turn interrupted rather than completing it silently', () => {
    const state = apply([...openTurn, { type: 'stream_disconnected' }]);

    expect(state.messages[0]).toMatchObject({ text: 'Tokyo in spr', status: 'interrupted' });
    expect(state.connection).toBe('closed');
  });

  it('marks a stopped turn differently from a dropped one', () => {
    // §7.4 asks for both words. Telling a user their connection failed when they pressed Stop is
    // a lie about their own action.
    const stopped = apply([...openTurn, { type: 'stream_cancelled' }]);

    expect(stopped.messages[0]?.status).toBe('stopped');
  });

  it('keeps the text that did arrive when a turn is interrupted', () => {
    const state = apply([...openTurn, { type: 'stream_disconnected' }]);

    expect(state.messages[0]?.text).toBe('Tokyo in spr');
  });

  it('continues an interrupted turn in place while reconnecting', () => {
    const resumed = apply([
      ...openTurn,
      { type: 'stream_disconnected' },
      { type: 'stream_reconnecting' },
      frame({ type: 'text_delta', messageId: null, text: 'ing' }),
    ]);

    expect(resumed.messages).toHaveLength(1);
    expect(resumed.messages[0]).toMatchObject({ text: 'Tokyo in spring', status: 'streaming' });
    expect(resumed.connection).toBe('streaming');
  });

  it('does not resurrect an interrupted turn once the connection has moved on', () => {
    const state = apply([
      ...openTurn,
      { type: 'stream_disconnected' },
      frame({ type: 'text_delta', messageId: null, text: 'a new answer' }),
    ]);

    expect(state.messages).toHaveLength(2);
    expect(state.messages[0]?.status).toBe('interrupted');
  });

  it('surfaces a typed error and marks the partial turn, without dropping either', () => {
    const state = apply([
      send('c1', 'Plan Japan'),
      ...openTurn,
      frame({
        type: 'error',
        error: {
          code: 'ai_timeout',
          message: 'provider timed out',
          details: {},
          requestId: 'req-7',
          i18nKey: 'errors.ai_timeout',
        },
      }),
    ]);

    expect(state.connection).toBe('failed');
    expect(state.error?.i18nKey).toBe('errors.ai_timeout');
    expect(state.messages.at(-1)?.status).toBe('interrupted');
  });

  it('marks the user message not sent when the request itself failed', () => {
    const state = apply([
      send('c1', 'Plan Japan'),
      {
        type: 'stream_failed',
        clientMessageId: 'c1',
        error: {
          code: 'rate_limited',
          message: 'slow down',
          details: {},
          requestId: null,
          i18nKey: 'errors.rate_limited',
        },
      },
    ]);

    expect(state.messages[0]?.status).toBe('failed');
  });

  it('revives the failed message on retry instead of adding a second one', () => {
    const failed = apply([
      send('c1', 'Plan Japan'),
      {
        type: 'stream_failed',
        clientMessageId: 'c1',
        error: {
          code: 'rate_limited',
          message: 'slow down',
          details: {},
          requestId: null,
          i18nKey: 'errors.rate_limited',
        },
      },
    ]);

    const retried = chatReducer(failed, send('c1', 'Plan Japan'));

    expect(retried.messages).toHaveLength(1);
    expect(retried.messages[0]?.status).toBe('pending');
    expect(retried.error).toBeNull();
  });
});

describe('a server replay after a completed turn (ADR 007 resume, F-39)', () => {
  // The shape `ChatTurnService.replay` produces: whole messages with their text already on the
  // `message_start`, no deltas at all, and a `done` whose stop reason is not a provider one. The
  // server can only send this because the turn had finished; the client has to render it as a
  // finished exchange rather than as a turn that never produced any text.
  const replayFrames = [
    frame(
      {
        type: 'message_start',
        messageId: 'u1',
        role: 'user',
        clientMessageId: 'c1',
        content: 'Kyoto in spring?',
        createdAt: '2026-07-01T09:00:00Z',
      },
      '1',
    ),
    frame(
      {
        type: 'message_start',
        messageId: 'a1',
        role: 'assistant',
        clientMessageId: null,
        content: 'Cherry blossom peaks in early April.',
        createdAt: '2026-07-01T09:00:01Z',
      },
      '2',
    ),
    frame({ type: 'message_end', messageId: 'a1', status: 'complete' }, '2'),
    frame({ type: 'done', stopReason: 'replay' }),
  ];

  it('renders the replayed answer, which arrives as content rather than as deltas', () => {
    const state = apply([{ type: 'stream_opened' }, ...replayFrames]);

    expect(state.messages.map((message) => message.text)).toEqual([
      'Kyoto in spring?',
      'Cherry blossom peaks in early April.',
    ]);
    expect(state.messages[1]?.status).toBe('complete');
    expect(state.connection).toBe('closed');
  });

  it('reconciles the optimistic bubble instead of showing the question twice', () => {
    // The reason the server replays the user echo too when the client kept no cursor: a reconnect
    // that lost everything still has its own bubble on screen, and nothing else can adopt it.
    const state = apply([send('c1', 'Kyoto in spring?'), { type: 'stream_opened' }, ...replayFrames]);

    expect(state.messages).toHaveLength(2);
    expect(state.messages[0]?.messageId).toBe('u1');
    expect(state.messages[0]?.status).toBe('sent');
  });

  it('accepts a stop reason that is not a provider one', () => {
    // `replay` is deliberately not `end_turn`; the client must not be checking against a closed set.
    const state = apply([{ type: 'stream_opened' }, ...replayFrames]);

    expect(state.error).toBeNull();
    expect(state.connection).toBe('closed');
  });
});

describe('replayed frames after a resume', () => {
  it('applies a frame once, however many times the server replays it', () => {
    const delta = frame({ type: 'text_delta', messageId: 'a1', text: 'Tokyo' }, '7');
    const state = apply([{ type: 'stream_opened' }, delta, delta, delta]);

    expect(state.messages[0]?.text).toBe('Tokyo');
    expect(state.lastEventId).toBe('7');
  });

  it('drops a frame from before the resume point', () => {
    const state = apply([
      { type: 'stream_opened' },
      frame({ type: 'text_delta', messageId: 'a1', text: 'Tokyo' }, '7'),
      frame({ type: 'text_delta', messageId: 'a1', text: 'stale' }, '3'),
      frame({ type: 'text_delta', messageId: 'a1', text: ' in spring' }, '8'),
    ]);

    expect(state.messages[0]?.text).toBe('Tokyo in spring');
  });

  it('applies every frame when the server does not number them', () => {
    const state = apply([
      { type: 'stream_opened' },
      frame({ type: 'text_delta', messageId: 'a1', text: 'a' }),
      frame({ type: 'text_delta', messageId: 'a1', text: 'b' }),
    ]);

    expect(state.messages[0]?.text).toBe('ab');
  });
});

describe('history', () => {
  it('loads a page in order and marks it loaded', () => {
    const state = apply([
      {
        type: 'history_loaded',
        conversationId: 'conv-1',
        messages: [
          persisted({ message_id: 'm2', seq: 2, role: 'assistant', content: 'hi there' }),
          persisted({ message_id: 'm1', seq: 1, content: 'hello' }),
        ],
      },
    ]);

    expect(state.conversationId).toBe('conv-1');
    expect(state.historyLoaded).toBe(true);
    expect(state.messages.map((message) => message.messageId)).toEqual(['m1', 'm2']);
    expect(state.messages[0]?.status).toBe('sent');
    expect(state.messages[1]?.status).toBe('complete');
  });

  it('shows a persisted partial answer as interrupted', () => {
    const state = apply([
      {
        type: 'history_loaded',
        conversationId: null,
        messages: [persisted({ message_id: 'm2', role: 'assistant', status: 'interrupted' })],
      },
    ]);

    expect(state.messages[0]?.status).toBe('interrupted');
  });

  it('merges an older page without duplicating the page already on screen', () => {
    const first = apply([
      { type: 'history_loaded', conversationId: null, messages: [persisted({ message_id: 'm5', seq: 5 })] },
    ]);

    const merged = chatReducer(first, {
      type: 'history_loaded',
      conversationId: null,
      messages: [persisted({ message_id: 'm4', seq: 4 }), persisted({ message_id: 'm5', seq: 5 })],
    });

    expect(merged.messages.map((message) => message.messageId)).toEqual(['m4', 'm5']);
  });

  it('drops tool rows from the transcript while still accepting them off the wire', () => {
    // §7.2 forbids showing internal tool identifiers or raw JSON, and the live stream already keeps
    // only "a tool is running" — so a reload has to agree with it. Parsing them and then not
    // rendering them is the split: a page containing one loads, and nothing leaks.
    const state = apply([
      {
        type: 'history_loaded',
        conversationId: 'conv-1',
        messages: [
          persisted({ message_id: 'q', seq: 1, role: 'user', content: 'How much is the JR pass?' }),
          persisted({ message_id: 'call', seq: 2, role: 'tool_call', content: '{"tool":"price_lookup"}' }),
          persisted({ message_id: 'result', seq: 3, role: 'tool_result', content: '{"jpy":50000}' }),
          persisted({ message_id: 'a', seq: 4, role: 'assistant', content: 'About 50,000 yen.' }),
          persisted({ message_id: 'trip', seq: 5, role: 'lifecycle_event', content: 'Trip created' }),
        ],
      },
    ]);

    expect(state.messages.map((message) => message.messageId)).toEqual(['q', 'a', 'trip']);
    expect(state.messages.map((message) => message.text)).not.toContain('{"jpy":50000}');
  });

  it('keeps a message still in flight when history arrives', () => {
    const state = apply([
      send('c1', 'Plan Japan'),
      { type: 'history_loaded', conversationId: null, messages: [persisted({ message_id: 'm1' })] },
    ]);

    expect(state.messages).toHaveLength(2);
    expect(state.messages.at(-1)).toMatchObject({ clientMessageId: 'c1', status: 'pending' });
  });

  it('does not let a background reload rewind a turn that is still streaming', () => {
    const streaming = apply([
      { type: 'stream_opened' },
      frame({
        type: 'message_start',
        messageId: 'a1',
        role: 'assistant',
        clientMessageId: null,
        content: null,
        createdAt: null,
      }),
      frame({ type: 'text_delta', messageId: 'a1', text: 'Tokyo in spring' }),
    ]);

    const reloaded = chatReducer(streaming, {
      type: 'history_loaded',
      conversationId: null,
      messages: [persisted({ message_id: 'a1', role: 'assistant', content: 'Tok', status: 'interrupted' })],
    });

    expect(reloaded.messages[0]).toMatchObject({ text: 'Tokyo in spring', status: 'streaming' });
  });
});

describe('isStreaming', () => {
  it('is true while a turn is open or reconnecting, and false once it settles', () => {
    expect(isStreaming(initialChatState)).toBe(false);
    expect(isStreaming(chatReducer(initialChatState, { type: 'stream_opened' }))).toBe(true);
    expect(isStreaming(chatReducer(initialChatState, { type: 'stream_reconnecting' }))).toBe(true);
    expect(isStreaming(chatReducer(initialChatState, { type: 'stream_disconnected' }))).toBe(false);
  });
});
