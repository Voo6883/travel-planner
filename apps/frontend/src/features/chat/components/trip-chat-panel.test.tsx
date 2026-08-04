import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import enChat from '@/locales/en/chat.json';
import { queryKeys } from '@/lib/query/query-keys';
import { messagesFor } from '@/test/render';
import { TripChatPanel } from './trip-chat-panel';

/**
 * The trip panel's one job beyond the shared `ChatPanel` is turning a `brief_updated` frame into a
 * cache invalidation (task 22, UC-C5-09), so that is what this asserts — on the very query keys the
 * brief editor reads from.
 */

const TRIP_ID = 'trip-22';

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

function stubChat(chunks: readonly string[]) {
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
      return Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers(),
        body: sse(chunks),
      } as unknown as Response);
    }),
  );
}

function renderWithSpiedClient(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  render(
    <NextIntlClientProvider locale="en" messages={messagesFor('en')}>
      <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>
    </NextIntlClientProvider>,
  );
  return invalidate;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('TripChatPanel', () => {
  it('invalidates the brief and the trip detail when a brief_updated frame arrives', async () => {
    stubChat([
      `event: brief_updated\ndata: {"trip_id":"${TRIP_ID}"}\n\n`,
      'event: done\ndata: {"stop_reason":"end_turn"}\n\n',
    ]);
    const user = userEvent.setup();

    const invalidate = renderWithSpiedClient(<TripChatPanel tripId={TRIP_ID} />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Two adults{Enter}');

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.trips.brief(TRIP_ID) }),
    );
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.trips.detail(TRIP_ID) });
  });

  it('does not invalidate when a turn produces no brief_updated frame', async () => {
    stubChat([
      'event: message_start\ndata: {"message_id":"a1","role":"assistant"}\n\n',
      'event: text_delta\ndata: {"text":"Where would you like to go?"}\n\n',
      'event: done\ndata: {"stop_reason":"end_turn"}\n\n',
    ]);
    const user = userEvent.setup();

    const invalidate = renderWithSpiedClient(<TripChatPanel tripId={TRIP_ID} />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Hello{Enter}');

    expect(await screen.findByText('Where would you like to go?')).toBeInTheDocument();
    expect(invalidate).not.toHaveBeenCalledWith({ queryKey: queryKeys.trips.brief(TRIP_ID) });
  });
});
