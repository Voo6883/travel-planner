import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { TripBrief } from '@/lib/api/trip-api';
import enTripBrief from '@/locales/en/trip_brief.json';
import { mockContract } from '@/test/contract-mock';
import { renderWithProviders } from '@/test/render';
import { TripBriefEditor } from './trip-brief-editor';

const tripId = '6f9619ff-8b86-d011-b42d-00c04fc964ff';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('TripBriefEditor', () => {
  it('renders the generated-contract fields from the brief response', async () => {
    mockContract({
      '/trips/{tripId}/brief': { body: brief({ departure_city: 'Kuala Lumpur' }) },
    });

    renderWithProviders(<TripBriefEditor tripId={tripId} />);

    expect(await screen.findByLabelText(enTripBrief.field.departure_city)).toHaveValue('Kuala Lumpur');
    expect(screen.getByText(enTripBrief.field.destinations)).toBeInTheDocument();
    expect(screen.getByText(enTripBrief.field.interests)).toBeInTheDocument();
    expect(screen.getByText(enTripBrief.save.idle)).toBeInTheDocument();
  });

  it('shows a conflict notice and keeps the focused draft value after a 409 refetch', async () => {
    const mock = stubConflictFlow();
    const user = userEvent.setup();

    renderWithProviders(<TripBriefEditor tripId={tripId} />);
    const departure = await screen.findByLabelText(enTripBrief.field.departure_city);

    await user.clear(departure);
    await user.type(departure, 'Penang');

    expect(await screen.findByText(enTripBrief.conflict.notice)).toBeInTheDocument();
    expect(departure).toHaveValue('Penang');
    expect(JSON.parse(mock.putBody() ?? '{}')).toMatchObject({ expected_version: 1, departure_city: 'Penang' });
  });
});

function stubConflictFlow() {
  const calls: RequestInit[] = [];
  let readCount = 0;

  vi.stubGlobal(
    'fetch',
    vi.fn((_url: string, init: RequestInit = {}) => {
      calls.push(init);
      if ((init.method ?? 'GET') === 'PUT') {
        return response(409, {
          code: 'version_conflict',
          message: 'Version conflict',
          details: { current_version: 2 },
        });
      }
      readCount += 1;
      const body = readCount === 1 ? brief({ departure_city: 'Kuala Lumpur' }) : brief({ version: 2 });
      return response(200, body);
    }),
  );

  return {
    putBody: () => calls.find((call) => call.method === 'PUT')?.body?.toString(),
  };
}

function response(status: number, body: unknown): Promise<Response> {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    json: async () => body,
  } as Response);
}

function brief(overrides: Partial<TripBrief> = {}): TripBrief {
  return {
    trip_id: tripId,
    status: 'DRAFT',
    destinations: ['kyoto'],
    dates: null,
    date_flexibility: null,
    departure_city: null,
    budget: null,
    party: null,
    interests: [],
    pace: null,
    clarification: { questions: [] },
    version: 1,
    created_at: '2026-08-03T00:00:00Z',
    updated_at: '2026-08-03T00:00:00Z',
    ...overrides,
  };
}
