import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RankedRecommendations, ResearchJob } from '@/lib/api/research-api';
import type { Trip } from '@/lib/api/trip-api';
import enResearch from '@/locales/en/research.json';
import msResearch from '@/locales/ms/research.json';
import { mockContract } from '@/test/contract-mock';
import { renderWithProviders } from '@/test/render';
import { ResearchPanel } from './research-panel';

const tripId = '6f9619ff-8b86-d011-b42d-00c04fc964ff';
const jobId = '11111111-2222-3333-4444-555555555555';
const recommendationId = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
const destinationId = 'ffffffff-1111-2222-3333-444444444444';
const researchRunId = '99999999-8888-7777-6666-555555555555';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ResearchPanel', () => {
  it('offers a start control on a complete brief and posts a run', async () => {
    const mock = mockContract({
      '/trips/{tripId}': { body: trip({ status: 'BRIEF_COMPLETE' }) },
      '/trips/{tripId}/research/run': { status: 202, body: job({ status: 'queued' }) },
      '/trips/{tripId}/research/jobs/{jobId}': { body: job({ status: 'queued' }) },
    });
    const user = userEvent.setup();

    renderWithProviders(<ResearchPanel tripId={tripId} />);
    const startButton = await screen.findByRole('button', { name: enResearch.panel.start });
    await user.click(startButton);

    await waitFor(() => {
      expect(mock.calls.some((call) => call.method === 'POST' && call.url.includes('/research/run'))).toBe(true);
    });
  });

  it('shows an in-progress state while the trip is researching', async () => {
    mockContract({ '/trips/{tripId}': { body: trip({ status: 'RESEARCH_RUNNING' }) } });

    renderWithProviders(<ResearchPanel tripId={tripId} />);

    expect(await screen.findByText(enResearch.panel.running_title)).toBeInTheDocument();
  });

  it('loads ranked recommendations when research is ready', async () => {
    mockContract({
      '/trips/{tripId}': { body: trip({ status: 'RESEARCH_READY' }) },
      '/trips/{tripId}/ranked-recommendations': { body: ranked() },
    });

    renderWithProviders(<ResearchPanel tripId={tripId} />);

    expect(await screen.findByText(enResearch.panel.ready_title)).toBeInTheDocument();
    expect(await screen.findByText(enResearch.results.title)).toBeInTheDocument();
    expect(await screen.findByText(/Strong culture fit/)).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: enResearch.results.plan_cta })).toBeInTheDocument();
  });

  it('shows the typed empty state when there is no confident result', async () => {
    mockContract({
      '/trips/{tripId}': { body: trip({ status: 'RESEARCH_READY' }) },
      '/trips/{tripId}/ranked-recommendations': {
        body: ranked({ no_confident_result: true, recommendations: [] }),
      },
    });

    renderWithProviders(<ResearchPanel tripId={tripId} />);

    expect(await screen.findByText(enResearch.results.empty_title)).toBeInTheDocument();
  });

  it('confirms Plan this trip only after the server accepts selection', async () => {
    const mock = mockContract({
      '/trips/{tripId}': { body: trip({ status: 'RESEARCH_READY' }) },
      '/trips/{tripId}/ranked-recommendations': { body: ranked() },
      '/trips/{tripId}/selected-recommendation': {
        body: trip({ status: 'DESTINATION_SELECTED', selected_recommendation_id: recommendationId }),
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<ResearchPanel tripId={tripId} />);
    await user.click(await screen.findByRole('button', { name: enResearch.results.plan_cta }));

    await waitFor(() => {
      expect(mock.calls.some((call) => call.method === 'POST' && call.url.includes('/selected-recommendation'))).toBe(
        true,
      );
    });
  });

  it('locks the control until the brief is complete', async () => {
    mockContract({ '/trips/{tripId}': { body: trip({ status: 'DRAFT' }) } });

    renderWithProviders(<ResearchPanel tripId={tripId} />);

    expect(await screen.findByText(enResearch.panel.locked)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: enResearch.panel.start })).not.toBeInTheDocument();
  });

  it('renders the start control in Malay', async () => {
    mockContract({ '/trips/{tripId}': { body: trip({ status: 'BRIEF_COMPLETE' }) } });

    renderWithProviders(<ResearchPanel tripId={tripId} />, { locale: 'ms' });

    expect(await screen.findByRole('button', { name: msResearch.panel.start })).toBeInTheDocument();
  });
});

function trip(overrides: Partial<Trip> = {}): Trip {
  return {
    trip_id: tripId,
    name: 'Kyoto',
    status: 'BRIEF_COMPLETE',
    selected_recommendation_id: null,
    version: 3,
    created_at: '2026-08-03T00:00:00Z',
    updated_at: '2026-08-03T00:00:00Z',
    ...overrides,
  };
}

function job(overrides: Partial<ResearchJob> = {}): ResearchJob {
  return {
    job_id: jobId,
    trip_id: tripId,
    status: 'queued',
    progress_pct: 0,
    attempts: 0,
    error_code: null,
    started_at: null,
    completed_at: null,
    ...overrides,
  };
}

function ranked(overrides: Partial<RankedRecommendations> = {}): RankedRecommendations {
  return {
    trip_id: tripId,
    research_run_id: researchRunId,
    no_confident_result: false,
    algorithm_version: 'destination-ranker-v1',
    selected_recommendation_id: null,
    recommendations: [
      {
        recommendation_id: recommendationId,
        destination_id: destinationId,
        destination_slug: 'kyoto-jp',
        country_code: 'JP',
        rank: 1,
        fit_score: 0.82,
        score_breakdown: {
          interest_match: 0.9,
          seasonality_fit: 0.8,
          price_fit: 0.7,
          area_coverage: 0.6,
          freshness_factor: 0.95,
          confidence: 0.88,
          fit_score: 0.82,
        },
        est_cost: { amount: '1200.00', currency: 'USD' },
        rationale: 'Strong culture fit',
        traveler_guide: {
          overview: 'Temples and gardens',
          why_now: 'Shoulder season',
          areas: ['Gion'],
          food: 'Kaiseki',
          highlights: ['Fushimi Inari'],
          mobility: 'Bus and walk',
          practical: 'Carry cash',
          local_app_pack: [{ usage: 'maps', name: 'Google Maps', slug: 'google-maps' }],
          source_refs: [{ source_ref: 'wikivoyage:kyoto', field_group: 'overview' }],
        },
        risks: ['Crowds at Fushimi'],
        best_window: 'spring',
        source_refs: [{ source_ref: 'wikivoyage:kyoto', field_group: 'overview' }],
        algorithm_version: 'destination-ranker-v1',
      },
    ],
    ...overrides,
  };
}
