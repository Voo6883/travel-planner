import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ResearchJob } from '@/lib/api/research-api';
import type { Trip } from '@/lib/api/trip-api';
import enResearch from '@/locales/en/research.json';
import msResearch from '@/locales/ms/research.json';
import { mockContract } from '@/test/contract-mock';
import { renderWithProviders } from '@/test/render';
import { ResearchJobPanel } from './research-job-panel';

const tripId = '6f9619ff-8b86-d011-b42d-00c04fc964ff';
const jobId = '11111111-2222-3333-4444-555555555555';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ResearchJobPanel', () => {
  it('offers a start control on a complete brief and posts a run', async () => {
    const mock = mockContract({
      '/trips/{tripId}': { body: trip({ status: 'BRIEF_COMPLETE' }) },
      '/trips/{tripId}/research/run': { status: 202, body: job({ status: 'queued' }) },
      '/trips/{tripId}/research/jobs/{jobId}': { body: job({ status: 'queued' }) },
    });
    const user = userEvent.setup();

    renderWithProviders(<ResearchJobPanel tripId={tripId} />);
    const startButton = await screen.findByRole('button', { name: enResearch.panel.start });
    await user.click(startButton);

    await waitFor(() => {
      expect(
        mock.calls.some((call) => call.method === 'POST' && call.url.includes('/research/run')),
      ).toBe(true);
    });
  });

  it('shows an in-progress state while the trip is researching', async () => {
    mockContract({ '/trips/{tripId}': { body: trip({ status: 'RESEARCH_RUNNING' }) } });

    renderWithProviders(<ResearchJobPanel tripId={tripId} />);

    expect(await screen.findByText(enResearch.panel.running_title)).toBeInTheDocument();
  });

  it('announces that recommendations are ready once research completes', async () => {
    mockContract({ '/trips/{tripId}': { body: trip({ status: 'RESEARCH_READY' }) } });

    renderWithProviders(<ResearchJobPanel tripId={tripId} />);

    expect(await screen.findByText(enResearch.panel.ready_title)).toBeInTheDocument();
  });

  it('locks the control until the brief is complete', async () => {
    mockContract({ '/trips/{tripId}': { body: trip({ status: 'DRAFT' }) } });

    renderWithProviders(<ResearchJobPanel tripId={tripId} />);

    expect(await screen.findByText(enResearch.panel.locked)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: enResearch.panel.start })).not.toBeInTheDocument();
  });

  it('renders the start control in Malay', async () => {
    mockContract({ '/trips/{tripId}': { body: trip({ status: 'BRIEF_COMPLETE' }) } });

    renderWithProviders(<ResearchJobPanel tripId={tripId} />, { locale: 'ms' });

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
