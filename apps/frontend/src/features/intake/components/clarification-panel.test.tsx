import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { TripBrief } from '@/lib/api/trip-api';
import enTripBrief from '@/locales/en/trip_brief.json';
import { mockContract } from '@/test/contract-mock';
import { renderWithProviders } from '@/test/render';
import { ClarificationPanel } from './clarification-panel';

const tripId = '6f9619ff-8b86-d011-b42d-00c04fc964ff';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ClarificationPanel', () => {
  it('submits typed answers for outstanding questions', async () => {
    const mock = mockContract({
      '/trips/{tripId}/brief/actions/answer-clarification': {
        body: brief({ status: 'BRIEF_COMPLETE', clarification: { questions: [] }, version: 4 }),
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<ClarificationPanel tripId={tripId} brief={brief()} />);
    await user.type(screen.getByLabelText(enTripBrief.clarify_departure), 'Kuala Lumpur');
    await user.type(screen.getByLabelText(enTripBrief.clarify_party), '2');
    await user.click(screen.getByRole('button', { name: enTripBrief.clarification.submit }));

    await waitFor(() => {
      expect(mock.lastCall().method).toBe('POST');
    });
    expect(JSON.parse(mock.lastCall().body ?? '{}')).toEqual({
      expected_version: 3,
      answers: [
        { question_id: 'departure_city', text: 'Kuala Lumpur' },
        { question_id: 'party_size', number: 2 },
      ],
    });
  });

  it('renders clarification prompts in Malay from prompt_key values', () => {
    renderWithProviders(<ClarificationPanel tripId={tripId} brief={brief()} />, { locale: 'ms' });

    expect(screen.getByText('Dari mana anda akan berlepas?')).toBeInTheDocument();
    expect(screen.queryByText(enTripBrief.clarify_departure)).not.toBeInTheDocument();
  });
});

function brief(overrides: Partial<TripBrief> = {}): TripBrief {
  return {
    trip_id: tripId,
    status: 'CLARIFICATION_NEEDED',
    destinations: [],
    surprise_me: false,
    dates: null,
    date_flexibility: null,
    departure_city: null,
    budget: null,
    party: null,
    interests: [],
    pace: null,
    clarification: {
      questions: [
        {
          id: 'departure_city',
          prompt_key: 'trip_brief.clarify_departure',
          type: 'TEXT',
          options: [],
          required: true,
        },
        {
          id: 'party_size',
          prompt_key: 'trip_brief.clarify_party',
          type: 'NUMBER',
          options: [],
          required: true,
        },
      ],
    },
    version: 3,
    created_at: '2026-08-03T00:00:00Z',
    updated_at: '2026-08-03T00:00:00Z',
    ...overrides,
  };
}
