import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import enChat from '@/locales/en/chat.json';
import { renderWithProviders } from '@/test/render';
import { PlannerHomePanel } from './planner-home-panel';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
}));

function stubChat(chunks: readonly string[]) {
  const encoder = new TextEncoder();
  let index = 0;
  const body = new ReadableStream<Uint8Array>({
    pull(controller) {
      if (index >= chunks.length) {
        controller.close();
        return;
      }
      controller.enqueue(encoder.encode(chunks[index] ?? ''));
      index += 1;
    },
  });
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
        body,
      } as unknown as Response);
    }),
  );
}

afterEach(() => {
  push.mockReset();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('PlannerHomePanel', () => {
  it('navigates from the typed trip_created event, not from assistant prose', async () => {
    stubChat([
      'event: trip_created\ndata: {"trip_id":"trip-42"}\n\n',
      'event: done\ndata: {"stop_reason":"end_turn"}\n\n',
    ]);
    const user = userEvent.setup();

    renderWithProviders(<PlannerHomePanel />);
    await user.type(screen.getByLabelText(enChat.composer.label), 'Japan in spring{Enter}');

    await waitFor(() => expect(push).toHaveBeenCalledWith('/trips/trip-42'));
    expect(push).toHaveBeenCalledTimes(1);
  });
});
