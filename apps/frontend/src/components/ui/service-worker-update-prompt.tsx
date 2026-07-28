'use client';

import { useSerwist } from '@serwist/next/react';
import { Button } from 'antd';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState } from 'react';

/**
 * "An update is ready" (design system §9.4).
 *
 * The worker is built with `skipWaiting: false`, so a new version installs and then parks in
 * `waiting` rather than replacing the running app mid-session. This banner is what lets the user
 * release it: "Refresh now" is primary, "Later" is secondary, and neither blocks the page.
 *
 * A banner in normal flow rather than an Ant `notification`: it must survive on a 320 px viewport
 * without covering the mobile bottom navigation, and an assertion about a toast portal is a much
 * weaker test than one about rendered content.
 */
export function ServiceWorkerUpdatePrompt() {
  // `common` is the namespace `lib/i18n/request.ts` actually loads; `pwa.*` is a group inside it.
  const t = useTranslations('common');
  const { serwist } = useSerwist();
  const [isUpdateReady, setIsUpdateReady] = useState(false);
  const [isDismissed, setIsDismissed] = useState(false);

  /**
   * Whether *this* page asked for the swap.
   *
   * `controlling` also fires on a first install, because `clientsClaim` is on — reloading then
   * would refresh every first-time visitor for no reason. Only a reload the user asked for is
   * legitimate, and a ref rather than state because the listener must read it without being
   * re-subscribed.
   */
  const hasAcceptedUpdate = useRef(false);

  useEffect(() => {
    if (!serwist) {
      return;
    }

    const onWaiting = () => setIsUpdateReady(true);
    const onControlling = () => {
      if (hasAcceptedUpdate.current) {
        window.location.reload();
      }
    };

    serwist.addEventListener('waiting', onWaiting);
    serwist.addEventListener('controlling', onControlling);
    return () => {
      serwist.removeEventListener('waiting', onWaiting);
      serwist.removeEventListener('controlling', onControlling);
    };
  }, [serwist]);

  if (!isUpdateReady || isDismissed) {
    return null;
  }

  const acceptUpdate = () => {
    hasAcceptedUpdate.current = true;
    // Tells the waiting worker to activate. The reload happens in the `controlling` handler
    // above, once it actually has, so the page never reloads onto the old assets.
    serwist?.messageSkipWaiting();
  };

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex w-full flex-wrap items-center gap-x-4 gap-y-2 border-b border-border-subtle bg-info-surface px-4 py-2 text-body-sm text-foreground"
    >
      <span>
        <span className="font-semibold">{t('pwa.update_ready_title')}</span>{' '}
        <span className="text-foreground-muted">{t('pwa.update_ready_body')}</span>
      </span>
      <span className="flex items-center gap-2">
        <Button size="small" type="primary" onClick={acceptUpdate}>
          {t('pwa.update_refresh_now')}
        </Button>
        <Button size="small" aria-label={t('pwa.update_dismiss')} onClick={() => setIsDismissed(true)}>
          {t('pwa.update_later')}
        </Button>
      </span>
    </div>
  );
}
