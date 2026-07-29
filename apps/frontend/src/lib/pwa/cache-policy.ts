/**
 * What the service worker is allowed to keep, and what it must always fetch (PLAN §4.2.11).
 *
 * This table is deliberately separate from `src/sw.ts`. `sw.ts` compiles for a `WebWorker` target
 * and is bundled by Serwist rather than by Next, so it is not reachable from a jsdom test — and
 * "an authenticated API response is never written to Cache Storage" is exactly the rule that has
 * to be tested rather than asserted. Keeping the *matching* here, free of any Serwist import,
 * makes it directly testable; `sw.ts` only maps each entry onto a strategy instance.
 *
 * ## Why this is not `defaultCache`
 *
 * PLAN §4.2.11's stack table names Serwist's `defaultCache`, but `defaultCache` contains
 *
 * ```ts
 * { matcher: ({ sameOrigin, url }) => sameOrigin && url.pathname.startsWith('/api/'),
 *   method: 'GET', handler: new NetworkFirst({ cacheName: 'apis', ... }) }
 * ```
 *
 * — same-origin API responses cached **network-first for 24 hours**. That is precisely the
 * conflict `docs/PLAN-COMPATIBILITY.md` already adjudicated ("API cache 'network-first' vs ADR
 * network-only → **network-only**", ADR 005), and under ADR 006 it is now actively dangerous:
 * task 11 moved the API to the frontend's own origin behind `rewrites()`, so `/api/v1/**` is
 * same-origin and that rule would capture *every authenticated GET* — one user's trips served
 * from Cache Storage to whoever opens the browser next.
 *
 * `defaultCache` also network-first caches page documents and RSC payloads. Design system §9.3
 * allows "Available offline" labelling "only when confirmed by implementation", and v1 confirms
 * no such thing, so documents stay network-only with the `/~offline` fallback — which is exactly
 * the row PLAN §4.2.11's caching-rules table gives them ("Offline navigation → Fallback to
 * `/~offline`", not "serve the cached page").
 *
 * The static-asset rules below mirror `defaultCache`'s safe subset.
 */

/** ADR 006 makes this same-origin. It is a path, not an origin, and that is the whole hazard. */
export const API_PATH_PREFIX = '/api/v1';

/**
 * Guard on `/api/` rather than `/api/v1/` on purpose. `/api/v2` does not exist yet and neither
 * does any route handler under `app/api/`, but if either ever appears it must be network-only by
 * default — a caching rule should never be something a future path silently opts into.
 */
const API_PATH_ROOT = '/api/';

export type CacheStrategyId = 'network-only' | 'cache-first' | 'stale-while-revalidate';

export interface CacheRule {
  /** Stable identifier, used by tests and by the cache names below. */
  readonly id: string;
  readonly strategy: CacheStrategyId;
  /** Cache Storage bucket. Absent for `network-only`, which by definition writes nothing. */
  readonly cacheName?: string;
  readonly maxEntries?: number;
  readonly maxAgeSeconds?: number;
  readonly matches: (url: URL, sameOrigin: boolean) => boolean;
}

const ONE_DAY_SECONDS = 24 * 60 * 60;
const ONE_WEEK_SECONDS = 7 * ONE_DAY_SECONDS;

/**
 * Builds a matcher for a cacheable same-origin asset.
 *
 * Every caching rule goes through here so that `!isApiPath(...)` is applied *in the rule itself*
 * rather than being inherited from the API rule's position at the top of the list. Ordering alone
 * looked sufficient — the API rule matches first, so nothing below it ever runs against an API
 * request — but it made the guarantee depend on a line's position in an array. It does not
 * survive someone inserting a rule above it or reordering for readability, and
 * `/api/v1/trips/42/cover.png` really does match the static-image pattern.
 *
 * With the guard here the invariant is a property of each rule, which is what `cache-policy.test`
 * asserts: *no* caching rule so much as matches an API URL, whatever order they are in.
 */
function cacheableAsset(pattern: RegExp | ((pathname: string) => boolean)) {
  const test = typeof pattern === 'function' ? pattern : (path: string) => pattern.test(path);
  return (url: URL, sameOrigin: boolean): boolean => sameOrigin && !isApiPath(url.pathname) && test(url.pathname);
}

/**
 * Evaluated in order — Serwist registers routes in array order and the first match responds.
 *
 * The API rule is first so that no later rule can ever claim an API request, whatever it matches
 * on. That ordering is load-bearing, not stylistic: `/api/v1/trips/x/photo.png` would otherwise
 * be swept up by the static-image rule, and `cache-policy.test.ts` fails if the order changes.
 */
export const cacheRules: readonly CacheRule[] = [
  {
    // ADR 005, PLAN §4.2.11: "Network-only — never cache API responses". Session cookies, trip
    // data, and booking state all travel this path.
    id: 'api-network-only',
    strategy: 'network-only',
    matches: (url, sameOrigin) => sameOrigin && isApiPath(url.pathname),
  },
  {
    // Content-hashed and immutable, so cache-first can never serve a stale build.
    id: 'next-static',
    strategy: 'cache-first',
    cacheName: 'next-static-assets',
    maxEntries: 64,
    maxAgeSeconds: ONE_DAY_SECONDS,
    matches: cacheableAsset((pathname) => pathname.startsWith('/_next/static/')),
  },
  {
    id: 'next-image',
    strategy: 'stale-while-revalidate',
    cacheName: 'next-image',
    maxEntries: 64,
    maxAgeSeconds: ONE_DAY_SECONDS,
    matches: cacheableAsset((pathname) => pathname === '/_next/image'),
  },
  {
    id: 'static-fonts',
    strategy: 'stale-while-revalidate',
    cacheName: 'static-font-assets',
    maxEntries: 8,
    maxAgeSeconds: ONE_WEEK_SECONDS,
    matches: cacheableAsset(/\.(?:eot|otf|ttc|ttf|woff|woff2)$/i),
  },
  {
    id: 'static-images',
    strategy: 'stale-while-revalidate',
    cacheName: 'static-image-assets',
    maxEntries: 64,
    maxAgeSeconds: ONE_WEEK_SECONDS,
    matches: cacheableAsset(/\.(?:jpg|jpeg|gif|png|svg|ico|webp)$/i),
  },
  {
    id: 'static-scripts',
    strategy: 'stale-while-revalidate',
    cacheName: 'static-js-assets',
    maxEntries: 48,
    maxAgeSeconds: ONE_DAY_SECONDS,
    matches: cacheableAsset(/\.(?:js|mjs)$/i),
  },
  {
    id: 'static-styles',
    strategy: 'stale-while-revalidate',
    cacheName: 'static-style-assets',
    maxEntries: 32,
    maxAgeSeconds: ONE_DAY_SECONDS,
    matches: cacheableAsset(/\.css$/i),
  },
  {
    /**
     * Everything else — page documents, RSC payloads, JSON, and every cross-origin request
     * (Firebase among them). Network-only, so nothing dynamic or third-party is ever persisted.
     *
     * This is also the rule the `/~offline` fallback hangs off: when a document request fails
     * here, Serwist's fallback plugin answers with the precached offline shell instead.
     */
    id: 'catch-all-network-only',
    strategy: 'network-only',
    matches: () => true,
  },
];

/** True for any path the API owns. Exported so `sw.ts` and the fallback matcher share one answer. */
export function isApiPath(pathname: string): boolean {
  return pathname === '/api' || pathname.startsWith(API_PATH_ROOT);
}

/** The branded shell the worker serves when a navigation cannot reach the network (PLAN §4.2.3). */
export const OFFLINE_FALLBACK_URL = '/~offline';

/**
 * Whether a failed request may be answered with the `/~offline` page.
 *
 * Serwist attaches the fallback to **every** runtime strategy, including the API's, so this
 * predicate — not the strategy list — is what stops a failed `GET /api/v1/trips` from resolving
 * with the offline page's HTML. That would be worse than a network error: the caller asked for
 * JSON and would get a `200` full of markup, so `apiRequest()` would throw a parse error instead
 * of the offline failure the UI knows how to show. The task brief requires the opposite —
 * "`/api/v1/**` fails normally offline rather than returning cached data".
 *
 * Documents only, for the same reason: a stylesheet or image request that falls back to an HTML
 * page is a corrupt asset, not a graceful degradation.
 */
export function isOfflineFallbackEligible(destination: string, url: string): boolean {
  return destination === 'document' && !isApiPath(new URL(url).pathname);
}

/** The rule that would handle `url`. Never `undefined` — the catch-all matches everything. */
export function resolveCacheRule(url: URL, sameOrigin: boolean): CacheRule {
  const rule = cacheRules.find((candidate) => candidate.matches(url, sameOrigin));
  // Unreachable while the catch-all is last; `noUncheckedIndexedAccess` still wants the guard,
  // and a policy list that stopped covering every request would be a silent hole.
  if (!rule) {
    throw new Error(`No cache rule matched ${url.pathname}`);
  }
  return rule;
}
