/**
 * Service worker source (ADR 005, PLAN §4.2.11).
 *
 * Serwist compiles this to `public/sw.js` during `next build`. **The generated file is build
 * output — never hand-edit it, and never commit it.**
 *
 * What the worker may keep lives in `lib/pwa/cache-policy.ts`, next to the test that proves
 * `/api/v1/**` cannot be cached. This file only turns each rule into a strategy instance.
 */

import {
  CacheFirst,
  ExpirationPlugin,
  NetworkOnly,
  Serwist,
  StaleWhileRevalidate,
  type PrecacheEntry,
  type RouteHandler,
  type RuntimeCaching,
  type SerwistGlobalConfig,
} from 'serwist';
import { cacheRules, isOfflineFallbackEligible, OFFLINE_FALLBACK_URL, type CacheRule } from '@/lib/pwa/cache-policy';

declare global {
  interface WorkerGlobalScope extends SerwistGlobalConfig {
    /** Injected by `@serwist/next` at build time: the precache manifest. */
    __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
  }
}

declare const self: ServiceWorkerGlobalScope;

/**
 * The rules from `cache-policy.ts`, in order, each bound to a strategy instance.
 *
 * Note what is *not* imported here: `defaultCache` from `@serwist/next/worker`. It caches
 * same-origin `/api/**` network-first, which ADR 006 turned into the entire authenticated API —
 * the full reasoning is in `cache-policy.ts`.
 *
 * It is not imported even as a reference. An earlier revision pulled it in as a compile-time
 * canary and discarded it with `void`; the bundler cannot tree-shake a value that is referenced,
 * so `cacheName:"apis"` and Serwist's page/RSC caches were emitted into `public/sw.js` as
 * unreachable dead code. Harmless at runtime, but anyone auditing the generated worker for "is
 * the API cached?" would find the exact string that says it is. The worker must not contain a
 * rule it must not use.
 */
const runtimeCaching: RuntimeCaching[] = cacheRules.map((rule) => ({
  matcher: ({ url, sameOrigin }) => rule.matches(url, sameOrigin),
  handler: buildHandler(rule),
}));

const serwist = new Serwist({
  precacheEntries: self.__SW_MANIFEST,

  /**
   * Left false so an update parks in `waiting` instead of taking over mid-session. Design system
   * §9.4 asks for "An update is ready" with an explicit "Refresh now", and skipping the wait
   * would swap the app's assets under a user who is part-way through a form.
   * `ServiceWorkerUpdatePrompt` is what releases it, via `SKIP_WAITING`.
   */
  skipWaiting: false,

  // Only meaningful on a first install, where there is no worker to displace: it lets the very
  // first page load end up controlled, so the offline shell works without a second visit.
  clientsClaim: true,

  /**
   * Off deliberately. Serwist never reads `event.preloadResponse`, so enabling preload would make
   * the browser issue a second, discarded request for every navigation.
   */
  navigationPreload: false,

  runtimeCaching,

  fallbacks: {
    entries: [
      {
        url: OFFLINE_FALLBACK_URL,
        // Predicate lives in `cache-policy.ts` so `cache-policy.test.ts` can prove that a failed
        // API request is *not* answered with the offline page's HTML.
        matcher: ({ request }) => isOfflineFallbackEligible(request.destination, request.url),
      },
    ],
  },
});

serwist.addEventListeners();

/** One strategy instance per rule. Expiration is attached only where something is written. */
function buildHandler(rule: CacheRule): RouteHandler {
  if (rule.strategy === 'network-only') {
    // No cacheName, no plugins, nothing written to Cache Storage — just `fetch`.
    return new NetworkOnly();
  }

  const options = {
    cacheName: rule.cacheName,
    plugins: [
      new ExpirationPlugin({
        maxEntries: rule.maxEntries,
        maxAgeSeconds: rule.maxAgeSeconds,
        maxAgeFrom: 'last-used' as const,
      }),
    ],
  };

  return rule.strategy === 'cache-first' ? new CacheFirst(options) : new StaleWhileRevalidate(options);
}
