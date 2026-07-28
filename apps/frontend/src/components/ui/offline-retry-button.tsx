'use client';

import { Button } from 'antd';
import { useTranslations } from 'next-intl';
import { useOnlineStatus } from '@/hooks/use-online-status';

/**
 * Retry control for the `/~offline` shell (design system §9.3).
 *
 * A full `location.reload()` rather than a router refresh: the offline page is served by the
 * service worker as a fallback for a navigation that failed, so there is no client-side route to
 * go back to — the user needs the original request attempted again from scratch.
 *
 * Disabled while the device reports no connection, because a retry that cannot possibly succeed
 * is the "stale action shown as available" the task brief rules out. `navigator.onLine` only
 * proves an interface exists, so the button is *enabled* optimistically the moment it flips back
 * — a captive portal is better handled by letting the user try and fail than by blocking them.
 */
export function OfflineRetryButton() {
  // `common`, not `pwa`: `lib/i18n/request.ts` loads exactly two namespaces, and the PWA strings
  // are a group inside `common`. `useTranslations('pwa')` would resolve nothing.
  const t = useTranslations('common');
  const isOnline = useOnlineStatus();

  return (
    <Button type="primary" disabled={!isOnline} onClick={() => window.location.reload()}>
      {t('pwa.offline_page_retry')}
    </Button>
  );
}
