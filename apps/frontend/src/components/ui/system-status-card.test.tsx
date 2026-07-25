import { render, screen, waitFor } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import en from '@/locales/en/common.json';
import ms from '@/locales/ms/common.json';
import { SystemStatusCard } from './system-status-card';

function renderWithLocale(ui: ReactElement, locale: 'en' | 'ms') {
  const messages = { common: locale === 'en' ? en : ms };
  return render(
    <NextIntlClientProvider locale={locale} messages={messages}>
      {ui}
    </NextIntlClientProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('SystemStatusCard', () => {
  it('renders without the backend and reports it as unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNREFUSED')));

    renderWithLocale(<SystemStatusCard />, 'en');

    // The shell must survive a dead backend — this is the Task 03 requirement, not a nicety.
    await waitFor(() => {
      expect(screen.getByText(en.status_unreachable)).toBeInTheDocument();
    });
    expect(screen.getByText(en.status_unreachable_hint)).toBeInTheDocument();
  });

  it('reports ready when the backend answers UP', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) }),
    );

    renderWithLocale(<SystemStatusCard />, 'en');

    await waitFor(() => {
      expect(screen.getAllByText(en.status_ready).length).toBeGreaterThan(0);
    });
  });

  it('treats a malformed payload as unreachable rather than trusting it', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ unexpected: true }) }),
    );

    renderWithLocale(<SystemStatusCard />, 'en');

    await waitFor(() => {
      expect(screen.getByText(en.status_unreachable)).toBeInTheDocument();
    });
  });

  it('renders Malay copy when the locale is ms', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNREFUSED')));

    renderWithLocale(<SystemStatusCard />, 'ms');

    expect(screen.getByText(ms.system_status_title)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText(ms.status_unreachable)).toBeInTheDocument();
    });
  });

  it('exposes an accessible section and announces status changes politely', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNREFUSED')));

    const { container } = renderWithLocale(<SystemStatusCard />, 'en');

    expect(screen.getByRole('region', { name: en.system_status_title })).toBeInTheDocument();
    expect(container.querySelector('[aria-live="polite"]')).not.toBeNull();

    // Let the probe settle so the state update happens inside act(), not after the test ends.
    await waitFor(() => {
      expect(screen.getByText(en.status_unreachable)).toBeInTheDocument();
    });
  });
});
