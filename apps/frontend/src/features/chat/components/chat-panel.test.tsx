import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PLANNER_CHAT_TARGET } from '@/lib/api/chat-api';
import enChat from '@/locales/en/chat.json';
import { renderWithProviders } from '@/test/render';
import { ChatPanel } from './chat-panel';

/**
 * The panel is asserted through what a user can perceive: the log region, the sender labels, the
 * delivery state in words, and — the one that matters most for task 20 — that assistant content is
 * rendered as **text**.
 */

function sse(chunks: readonly string[]): ReadableStream<Uint8Array> {
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

function stubChat(chunks: readonly string[], postStatus = 200) {
  vi.stubGlobal(
    'fetch',
    vi.fn((_url: string, init: RequestInit = {}) => {
      if ((init.method ?? 'GET') === 'GET') {
        return Promise.resolve({
          ok: true,
          status: 200,
          headers: new Headers(),
          json: async () => ({ page: 0, page_size: 30, total: 0, items: [] }),
        } as unknown as Response);
      }
      if (postStatus !== 200) {
        return Promise.resolve({
          ok: false,
          status: postStatus,
          headers: new Headers(),
          json: async () => ({ code: 'ai_unavailable', message: 'down' }),
        } as unknown as Response);
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers(),
        body: sse(chunks),
      } as unknown as Response);
    }),
  );
}

const assistantTurn = (text: string) => [
  'event: message_start\ndata: {"message_id":"a1","role":"assistant"}\n\n',
  `event: text_delta\ndata: ${JSON.stringify({ text })}\n\n`,
  'event: done\ndata: {"stop_reason":"end_turn"}\n\n',
];

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('ChatPanel', () => {
  it('offers an empty state that says what to do next', async () => {
    stubChat([]);

    renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} />);

    expect(await screen.findByText(enChat.panel.empty_title)).toBeInTheDocument();
  });

  it('shows the user message and the streamed answer in a named log region', async () => {
    stubChat(assistantTurn('Tokyo in spring is lovely.'));
    const user = userEvent.setup();

    renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Plan Japan');
    await user.click(screen.getByRole('button', { name: enChat.composer.send }));

    expect(await screen.findByText('Tokyo in spring is lovely.')).toBeInTheDocument();
    expect(screen.getByText('Plan Japan')).toBeInTheDocument();
    // §7.1: the history is a named `role="log"` region so updates are announced as appends.
    expect(screen.getByRole('log', { name: enChat.panel.log_label })).toBeInTheDocument();
  });

  it('sends on Enter', async () => {
    stubChat(assistantTurn('Noted.'));
    const user = userEvent.setup();

    renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Plan Japan{Enter}');

    expect(await screen.findByText('Noted.')).toBeInTheDocument();
  });

  it('renders assistant markup as characters, not as HTML (task 36 owns Markdown)', async () => {
    const hostile = '<img src=x onerror="alert(1)"> **bold**';
    stubChat(assistantTurn(hostile));
    const user = userEvent.setup();

    const { container } = renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Plan Japan{Enter}');

    expect(await screen.findByText(hostile)).toBeInTheDocument();
    // The tag arrived as text and must stay text until task 36 adds sanitised Markdown rendering.
    expect(container.querySelector('img')).toBeNull();
  });

  it('marks a partial answer as interrupted rather than presenting it as finished', async () => {
    // No `done` frame: the body simply stops, which ADR 007 says is a cut connection.
    stubChat([
      'event: message_start\ndata: {"message_id":"a1","role":"assistant"}\n\n',
      'event: text_delta\ndata: {"text":"Tokyo in spr"}\n\n',
    ]);
    const user = userEvent.setup();

    renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Plan Japan{Enter}');

    expect(await screen.findByText(enChat.status.interrupted)).toBeInTheDocument();
    expect(screen.getByText('Tokyo in spr')).toBeInTheDocument();
  });

  it('keeps a failed message visible as "Not sent" with a retry', async () => {
    stubChat([], 503);
    const user = userEvent.setup();

    renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Plan Japan{Enter}');

    expect(await screen.findByText(enChat.status.failed)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toBeInTheDocument();
    // §7.3: the words the user typed are still on screen — they are never asked to retype.
    expect(screen.getByText('Plan Japan')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: enChat.actions.retry }).length).toBeGreaterThan(0);
  });

  it('announces the handoff and reports the new trip id once', async () => {
    stubChat([
      'event: trip_created\ndata: {"trip_id":"trip-9"}\n\n',
      'event: done\ndata: {"stop_reason":"end_turn"}\n\n',
    ]);
    const onTripCreated = vi.fn();
    const user = userEvent.setup();

    renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} onTripCreated={onTripCreated} />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Plan Japan{Enter}');

    expect(await screen.findByText(enChat.status.trip_created)).toBeInTheDocument();
    await waitFor(() => expect(onTripCreated).toHaveBeenCalledWith('trip-9'));
    expect(onTripCreated).toHaveBeenCalledTimes(1);
  });

  it('offers suggested prompts on the planner home that send through the composer', async () => {
    stubChat(assistantTurn('Where would you like to go?'));
    const user = userEvent.setup();

    renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} showSuggestedPrompts />);

    await user.click(screen.getByRole('button', { name: enChat.suggestions.prompts.plan_trip }));

    expect(await screen.findByText('Where would you like to go?')).toBeInTheDocument();
    expect(screen.getByText(enChat.suggestions.prompts.plan_trip)).toBeInTheDocument();
  });

  it('translates the whole panel into Malay', async () => {
    stubChat([]);

    renderWithProviders(<ChatPanel target={PLANNER_CHAT_TARGET} />, { locale: 'ms' });

    // §11.3: a hard-coded English label survives the `en` tests and fails here.
    expect(await screen.findByText('Mula merancang')).toBeInTheDocument();
    expect(screen.queryByText(enChat.panel.empty_title)).not.toBeInTheDocument();
  });
});
