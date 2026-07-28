'use client';

import { useTranslations } from 'next-intl';
import { useOnlineStatus } from '@/hooks/use-online-status';

/**
 * Persistent offline notice (§9.3).
 *
 * Neutral, not an error: being offline is a state of the device, not a failure of the app, and
 * §9.3 asks for "persistent neutral banner plus availability detail". It never claims that
 * anything completed while offline.
 *
 * `role="status"` rather than `role="alert"` so it is announced without interrupting; it renders
 * nothing at all while online, which is exactly what §9.3 specifies for the online state.
 */
export function OfflineBanner() {
  const t = useTranslations('common');
  const isOnline = useOnlineStatus();

  if (isOnline) {
    return null;
  }

  return (
    <div
      role="status"
      aria-live="polite"
      className="w-full border-b border-border-subtle bg-surface-subtle px-4 py-2 text-body-sm text-foreground-muted"
    >
      <span className="font-semibold text-foreground">{t('states.offline_title')}</span>{' '}
      {t('states.offline_body')}
    </div>
  );
}
