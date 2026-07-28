'use client';

import { SerwistProvider } from '@serwist/next/react';
import type { ReactNode } from 'react';

/** Serwist writes the worker here (`swDest` in `next.config.ts`). */
export const SERVICE_WORKER_URL = '/sw.js';

/**
 * Registers the service worker and exposes it to `ServiceWorkerUpdatePrompt` (ADR 005).
 *
 * `@serwist/next` can register the worker by itself, but then nothing in React holds a reference
 * to it — and design system §9.4 needs one to offer "An update is ready". `next.config.ts`
 * therefore sets `register: false` and hands the job to this provider.
 */
export function ServiceWorkerProvider({ children }: { children: ReactNode }) {
  return (
    <SerwistProvider
      swUrl={SERVICE_WORKER_URL}
      // The worker is not built in development (`disable` in `next.config.ts`), so registering
      // would request a `/sw.js` that 404s on every page load.
      disable={process.env.NODE_ENV === 'development'}
      /**
       * Off: it posts `CACHE_URLS` for each visited pathname, which would write authenticated
       * page documents into Cache Storage — exactly what `lib/pwa/cache-policy.ts` keeps out.
       */
      cacheOnNavigation={false}
      /**
       * Off: Serwist's default reloads the page the moment connectivity returns. §9.3 requires an
       * unsent chat draft to survive as a draft, and an unannounced reload discards it. The
       * "Back online" recovery is `OfflineBanner`'s job, not a page refresh.
       */
      reloadOnOnline={false}
    >
      {children}
    </SerwistProvider>
  );
}
