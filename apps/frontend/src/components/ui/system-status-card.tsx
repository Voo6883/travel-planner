'use client';

import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { fetchBackendStatus, type BackendStatus } from '@/lib/api/health-client';

type ProbeState = 'checking' | BackendStatus;

/**
 * Shows whether the backend is reachable.
 *
 * Renders fully without the backend — an unreachable API is a displayed state, not an error
 * boundary. Uses plain state rather than React Query because there is no cache to share yet;
 * tasks/06/11 move this onto the generated client and a query hook.
 */
export function SystemStatusCard() {
  const t = useTranslations('common');
  const [backend, setBackend] = useState<ProbeState>('checking');

  useEffect(() => {
    const controller = new AbortController();
    fetchBackendStatus(controller.signal)
      .then(setBackend)
      .catch(() => setBackend('unreachable'));
    return () => controller.abort();
  }, []);

  return (
    <section
      aria-labelledby="system-status-heading"
      className="rounded-lg border border-border-subtle bg-surface p-4 sm:p-6"
    >
      <h2 id="system-status-heading" className="text-base font-medium">
        {t('system_status_title')}
      </h2>

      <dl className="mt-4 flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-sm text-foreground-muted">{t('system_status_frontend')}</dt>
          <dd className="text-sm font-medium text-success">{t('status_ready')}</dd>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-sm text-foreground-muted">{t('system_status_backend')}</dt>
          <dd
            className="text-sm font-medium"
            style={{ color: backend === 'ready' ? 'var(--success)' : 'var(--foreground-muted)' }}
          >
            {/* aria-live so the resolved status is announced, not silently swapped in. */}
            <span aria-live="polite">
              {backend === 'checking' ? t('status_checking') : null}
              {backend === 'ready' ? t('status_ready') : null}
              {backend === 'unreachable' ? t('status_unreachable') : null}
            </span>
          </dd>
        </div>
      </dl>

      {backend === 'unreachable' ? (
        <p className="mt-3 text-sm text-foreground-subtle">{t('status_unreachable_hint')}</p>
      ) : null}
    </section>
  );
}
