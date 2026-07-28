'use client';

import { useTranslations } from 'next-intl';
import { useBackendStatus } from '@/hooks/use-backend-status';

/**
 * Shows whether the backend is reachable.
 *
 * Renders fully without the backend — an unreachable API is a displayed state, not an error
 * boundary. Now reads through React Query (task 11), so the probe is shared and cached rather
 * than re-run by every mount.
 */
export function SystemStatusCard() {
  const t = useTranslations('common');
  const { data, isPending } = useBackendStatus();
  const isReady = data === 'ready';

  return (
    <section
      aria-labelledby="system-status-heading"
      className="rounded-lg border border-border-subtle bg-surface p-4 sm:p-6"
    >
      <h2 id="system-status-heading" className="m-0 text-title text-foreground">
        {t('system_status_title')}
      </h2>

      <dl className="mt-4 flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-body-sm text-foreground-muted">{t('system_status_frontend')}</dt>
          <dd className="m-0 text-body-sm font-medium text-success">{t('status_ready')}</dd>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <dt className="text-body-sm text-foreground-muted">{t('system_status_backend')}</dt>
          <dd className={`m-0 text-body-sm font-medium ${isReady ? 'text-success' : 'text-foreground-muted'}`}>
            {/* aria-live so the resolved status is announced, not silently swapped in. */}
            <span aria-live="polite">
              {isPending ? t('status_checking') : null}
              {!isPending && isReady ? t('status_ready') : null}
              {!isPending && !isReady ? t('status_unreachable') : null}
            </span>
          </dd>
        </div>
      </dl>

      {!isPending && !isReady ? (
        <p className="mb-0 mt-3 text-body-sm text-foreground-subtle">{t('status_unreachable_hint')}</p>
      ) : null}
    </section>
  );
}
