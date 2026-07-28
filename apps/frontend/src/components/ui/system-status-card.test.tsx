import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import en from '@/locales/en/common.json';
import ms from '@/locales/ms/common.json';
import { renderWithProviders } from '@/test/render';
import { SystemStatusCard } from './system-status-card';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('SystemStatusCard', () => {
  it('renders without the backend and reports it as unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNREFUSED')));

    renderWithProviders(<SystemStatusCard />);

    // The shell must survive a dead backend — this is the Task 03 requirement, not a nicety.
    await waitFor(() => {
      expect(screen.getByText(en.status_unreachable)).toBeInTheDocument();
    });
    expect(screen.getByText(en.status_unreachable_hint)).toBeInTheDocument();
  });

  it('reports ready when the backend answers UP', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ status: 'UP' }) }),
    );

    renderWithProviders(<SystemStatusCard />);

    await waitFor(() => {
      expect(screen.getAllByText(en.status_ready).length).toBeGreaterThan(0);
    });
  });

  it('treats a malformed payload as unreachable rather than trusting it', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({ unexpected: true }) }),
    );

    renderWithProviders(<SystemStatusCard />);

    await waitFor(() => {
      expect(screen.getByText(en.status_unreachable)).toBeInTheDocument();
    });
  });

  it('renders Malay copy when the locale is ms', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNREFUSED')));

    renderWithProviders(<SystemStatusCard />, { locale: 'ms' });

    expect(screen.getByText(ms.system_status_title)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText(ms.status_unreachable)).toBeInTheDocument();
    });
  });

  it('exposes an accessible section and announces status changes politely', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNREFUSED')));

    const { container } = renderWithProviders(<SystemStatusCard />);

    expect(screen.getByRole('region', { name: en.system_status_title })).toBeInTheDocument();
    expect(container.querySelector('[aria-live="polite"]')).not.toBeNull();

    // Let the probe settle so the state update happens inside act(), not after the test ends.
    await waitFor(() => {
      expect(screen.getByText(en.status_unreachable)).toBeInTheDocument();
    });
  });
});
