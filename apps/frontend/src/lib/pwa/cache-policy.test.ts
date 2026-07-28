import { describe, expect, it } from 'vitest';
import {
  API_PATH_PREFIX,
  cacheRules,
  isApiPath,
  isOfflineFallbackEligible,
  OFFLINE_FALLBACK_URL,
  resolveCacheRule,
} from './cache-policy';

/**
 * The guard rail for ADR 005's single hard rule: **`/api/v1/**` is never cached.**
 *
 * This is a cross-user data-leak test, not a performance test. ADR 006 moved the API onto the
 * frontend's own origin behind `rewrites()`, so the service worker now sees every authenticated
 * request as same-origin. A single rule that caches "same-origin GETs" — which is what Serwist's
 * own `defaultCache` ships — would put `/auth/me`, trip contents, and the admin user list into
 * Cache Storage, where the next person to open the browser gets them.
 *
 * Grepping the generated `public/sw.js` for "api/v1" cannot prove this: the string is absent both
 * when the API is correctly excluded and when the worker was simply never told about it. These
 * tests assert the behaviour instead, over the same table `src/sw.ts` compiles into routes, so a
 * future rule that starts matching the API fails the build rather than shipping quietly.
 */

const ORIGIN = 'https://planner.example';

/** Realistic authenticated endpoints, plus the shapes that tempt a static-asset matcher. */
const API_URLS = [
  '/api/v1/auth/me',
  '/api/v1/auth/login',
  '/api/v1/trips',
  '/api/v1/trips/1f0c9b1e-0000-4000-8000-000000000000',
  '/api/v1/trips/42/itinerary',
  '/api/v1/admin/users?page=0&size=20',
  '/api/v1/health',
  // Extensions are the trap: every one of these is claimed by a static-asset rule if the API
  // rule is not evaluated first.
  '/api/v1/trips/42/export.json',
  '/api/v1/trips/42/cover.png',
  '/api/v1/trips/42/report.csv',
  '/api/v1/assets/bundle.js',
  '/api/v1/assets/theme.css',
  '/api/v1/fonts/inter.woff2',
];

describe('cache policy: /api/v1 is network-only', () => {
  it.each(API_URLS)('never caches %s', (pathname) => {
    const rule = resolveCacheRule(new URL(pathname, ORIGIN), true);

    expect(rule.strategy).toBe('network-only');
    // A network-only rule that somehow carried a cache name would still be writing to Cache
    // Storage through an expiration plugin.
    expect(rule.cacheName).toBeUndefined();
  });

  it('resolves every API request through the dedicated API rule, not the catch-all', () => {
    for (const pathname of API_URLS) {
      expect(resolveCacheRule(new URL(pathname, ORIGIN), true).id).toBe('api-network-only');
    }
  });

  /**
   * The regression test proper. Every rule that writes to Cache Storage is asked directly whether
   * it would match an API URL — so adding a caching rule that matches `/api/**` fails here even
   * if it is ordered after the API rule and would never actually run today. Order is a fragile
   * thing to rely on; this makes the invariant order-independent.
   */
  it('has no caching rule that so much as matches an API URL', () => {
    const cachingRules = cacheRules.filter((rule) => rule.strategy !== 'network-only');
    expect(cachingRules.length).toBeGreaterThan(0);

    for (const rule of cachingRules) {
      for (const pathname of API_URLS) {
        expect(
          rule.matches(new URL(pathname, ORIGIN), true),
          `rule "${rule.id}" matches ${pathname}; ADR 005 forbids caching the API`,
        ).toBe(false);
      }
    }
  });

  it('keeps the API rule first, so nothing can claim an API request before it', () => {
    expect(cacheRules[0]?.id).toBe('api-network-only');
  });

  it('guards the whole /api namespace, not just the v1 prefix', () => {
    expect(API_PATH_PREFIX).toBe('/api/v1');
    expect(isApiPath('/api/v1/trips')).toBe(true);
    expect(isApiPath('/api/v2/trips')).toBe(true);
    expect(isApiPath('/api')).toBe(true);
    // Must not swallow unrelated routes that merely start with the same letters.
    expect(isApiPath('/apifoo')).toBe(false);
    expect(isApiPath('/trips')).toBe(false);
  });

  /**
   * A cross-origin `/api/v1/...` is somebody else's API, not ours. It still must not be cached —
   * it falls to the catch-all, which is also network-only.
   */
  it('does not cache a cross-origin request either', () => {
    const rule = resolveCacheRule(new URL('https://elsewhere.example/api/v1/trips'), false);
    expect(rule.strategy).toBe('network-only');
  });
});

describe('cache policy: what may be cached', () => {
  it.each([
    ['/_next/static/chunks/main-abc123.js', 'next-static', 'cache-first'],
    ['/_next/image', 'next-image', 'stale-while-revalidate'],
    ['/icons/icon-192.png', 'static-images', 'stale-while-revalidate'],
    ['/fonts/inter.woff2', 'static-fonts', 'stale-while-revalidate'],
  ])('serves %s from the %s rule', (pathname, expectedId, expectedStrategy) => {
    const rule = resolveCacheRule(new URL(pathname, ORIGIN), true);
    expect(rule.id).toBe(expectedId);
    expect(rule.strategy).toBe(expectedStrategy);
  });

  /**
   * Page documents and RSC payloads stay network-only. Design system §9.3 allows trip content to
   * be labelled "Available offline" *only when confirmed by implementation*, and v1 confirms no
   * such thing — so an authenticated page must not sit in Cache Storage claiming otherwise.
   * Offline navigation is answered by the `/~offline` fallback instead (PLAN §4.2.11).
   */
  it.each(['/', '/trips', '/trips/42', '/settings', '/admin/users', '/sign-in'])(
    'does not cache the %s document',
    (pathname) => {
      const rule = resolveCacheRule(new URL(pathname, ORIGIN), true);
      expect(rule.strategy).toBe('network-only');
      expect(rule.id).toBe('catch-all-network-only');
    },
  );

  it('caches nothing cross-origin, so no third-party response is ever persisted', () => {
    const crossOrigin = [
      'https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp',
      'https://cdn.example/logo.png',
      'https://cdn.example/widget.js',
    ];
    for (const url of crossOrigin) {
      expect(resolveCacheRule(new URL(url), false).strategy).toBe('network-only');
    }
  });

  it('gives every caching rule a distinct bucket and an expiration bound', () => {
    const cachingRules = cacheRules.filter((rule) => rule.strategy !== 'network-only');
    const names = cachingRules.map((rule) => rule.cacheName);

    expect(new Set(names).size).toBe(names.length);
    for (const rule of cachingRules) {
      expect(rule.cacheName, `rule ${rule.id}`).toBeTruthy();
      // Unbounded caches are how a service worker quietly fills a phone's storage quota.
      expect(rule.maxEntries, `rule ${rule.id}`).toBeGreaterThan(0);
      expect(rule.maxAgeSeconds, `rule ${rule.id}`).toBeGreaterThan(0);
    }
  });

  it('ends with a catch-all so no request escapes the policy', () => {
    expect(cacheRules[cacheRules.length - 1]?.id).toBe('catch-all-network-only');
    expect(resolveCacheRule(new URL('/anything/at/all', ORIGIN), true).strategy).toBe(
      'network-only',
    );
  });
});

/**
 * The second half of the exclusion. Even with no caching rule matching the API, the worker could
 * still *answer* an API request from the precache — Serwist attaches the offline fallback to every
 * runtime strategy, the network-only API rule included. These assert it does not.
 */
describe('offline fallback: serves the shell for navigations only', () => {
  it('points at the route PLAN §4.2.3 reserves for it', () => {
    expect(OFFLINE_FALLBACK_URL).toBe('/~offline');
  });

  it.each(['/', '/trips', '/trips/42', '/settings'])(
    'answers a failed navigation to %s with the offline shell',
    (pathname) => {
      expect(isOfflineFallbackEligible('document', `${ORIGIN}${pathname}`)).toBe(true);
    },
  );

  /**
   * The important one: an API call that fails offline must fail, not resolve with HTML. Returning
   * the offline page here would hand `apiRequest()` a `200` whose body is markup, turning a clean
   * network error into a JSON parse error the UI cannot map to a message.
   */
  it.each([
    '/api/v1/auth/me',
    '/api/v1/trips',
    '/api/v1/trips/42/itinerary',
    '/api/v1/admin/users',
  ])('lets %s fail normally instead of returning the shell', (pathname) => {
    expect(isOfflineFallbackEligible('', `${ORIGIN}${pathname}`)).toBe(false);
    // Even if a browser ever labelled an API fetch a document navigation, the path check holds.
    expect(isOfflineFallbackEligible('document', `${ORIGIN}${pathname}`)).toBe(false);
  });

  it.each(['image', 'script', 'style', 'font', 'fetch', ''])(
    'does not substitute the shell for a failed %s request',
    (destination) => {
      expect(isOfflineFallbackEligible(destination, `${ORIGIN}/assets/thing.js`)).toBe(false);
    },
  );
});
