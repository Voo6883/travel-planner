import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import withSerwistInit from '@serwist/next';
import createNextIntlPlugin from 'next-intl/plugin';
import type { NextConfig } from 'next';

const withNextIntl = createNextIntlPlugin('./src/lib/i18n/request.ts');

/**
 * PWA service worker (ADR 005, PLAN §4.2.11).
 *
 * Both plugins are pure config transforms, so composing them is safe in either order — each
 * returns the config it was given with its own webpack/loader additions merged in, and neither
 * replaces `rewrites`. `withSerwist` is applied outermost only so that Serwist sees the fully
 * resolved config. `.next/routes-manifest.json` is the check that the ADR 006 proxy survived.
 */
const withSerwist = withSerwistInit({
  swSrc: 'src/sw.ts',
  swDest: 'public/sw.js',

  /**
   * No worker in development. A service worker caching a dev build makes every edit look like it
   * did not apply, and PLAN §4.2.11 locks the behaviour ("SW **disabled** in development").
   */
  disable: process.env.NODE_ENV === 'development',

  /**
   * `ServiceWorkerProvider` registers it instead, so React holds a reference to the worker and
   * can offer §9.4's "An update is ready" when one is waiting.
   */
  register: false,

  /**
   * The precache manifest, on top of the webpack build assets Serwist collects automatically.
   *
   * **This option replaces the `public/` scan, it does not extend it.** `@serwist/next` reads
   * `additionalPrecacheEntries ?? globSync(globPublicPatterns)` — supplying entries here silently
   * turns the public-folder scan off, which is why `publicAssetEntries()` below reproduces it
   * rather than leaving `public/` to Serwist. Passing only the offline page made the icons quietly
   * stop being precached, and nothing warned about it.
   *
   * `/~offline` has to be listed because Serwist resolves `fallbacks.entries` against the
   * precache, not the network, and the route is request-time rendered (it reads the locale
   * cookie) so no build-time file exists for a glob to find.
   */
  additionalPrecacheEntries: [
    ...publicAssetEntries(),
    // `revision` ties the cached copy to the build. Without one the entry is treated as immutable
    // and a redeployed offline page would never replace the installed one — which matters because
    // the page's HTML embeds hashed `_next/static` URLs that change on every build.
    { url: '/~offline', revision: buildRevision() },
  ],
});

/**
 * Everything in `public/`, content-hashed, minus the worker itself.
 *
 * Excluding `sw.js` is not optional: a worker that precaches its own previous build pins it in
 * Cache Storage and can end up unable to update. Serwist's own scan excludes it for the same
 * reason, and this mirrors that behaviour now that the scan is disabled.
 */
function publicAssetEntries(): { url: string; revision: string }[] {
  const publicDir = join(import.meta.dirname, 'public');
  if (!existsSync(publicDir)) {
    return [];
  }

  return readdirSync(publicDir, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => relative(publicDir, join(entry.parentPath, entry.name)).split(sep).join('/'))
    .filter((file) => file !== 'sw.js' && file !== 'sw.js.map' && file !== '.gitkeep')
    .map((file) => ({
      url: `/${file}`,
      revision: createHash('sha256')
        .update(readFileSync(join(publicDir, file)))
        .digest('hex')
        .slice(0, 16),
    }));
}

/**
 * A per-build identifier for precache entries that are not content-hashed.
 *
 * `SOURCE_DATE_EPOCH` first so a reproducible build stays reproducible; otherwise the build
 * timestamp, which changes exactly when a new image is built and is what invalidates the cached
 * offline page on deploy.
 */
function buildRevision(): string {
  return process.env.SOURCE_DATE_EPOCH ?? Date.now().toString(36);
}

/**
 * Where the Next.js **server** reaches Spring Boot (ADR 006). Server-only on purpose: it is not
 * `NEXT_PUBLIC_*`, so it never reaches the browser, and the browser never needs it — every call
 * from a page is same-origin and lands on the rewrite below.
 *
 * `http://backend:8080` inside Compose, `http://localhost:8080` when the frontend runs on the host.
 */
const backendInternalUrl = (process.env.BACKEND_INTERNAL_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Emits a self-contained server bundle so the runtime image needs no node_modules
  // (docker/frontend/Dockerfile).
  output: 'standalone',

  /**
   * The monorepo has two lockfiles — this app's, and the root one for the orchestration-only
   * `package.json`. Next.js infers the workspace root by walking up to the nearest lockfile, finds
   * the repo root, and warns that its guess may be wrong. It is wrong: no frontend dependency
   * resolves from there, and a root-anchored trace would sweep apps/backend into the standalone
   * output. Pinning it to this directory matches what the Docker build already sees, since
   * docker/frontend/Dockerfile uses apps/frontend as its build context.
   */
  outputFileTracingRoot: import.meta.dirname,
  typescript: {
    // Never ship on a broken type-check; `npm run typecheck` must stay meaningful.
    ignoreBuildErrors: false,
  },
  eslint: {
    ignoreDuringBuilds: false,
  },

  /**
   * ADR 006 — the browser only ever talks to the frontend origin.
   *
   * `tp_session` is `httpOnly; SameSite=Lax`, so a cross-site `fetch(:8080)` from `:3000` never
   * carries it and every authenticated request fails. Proxying `/api/v1/**` through Next.js makes
   * the session cookie first-party, removes the need for CORS on browser traffic, and keeps
   * `SameSite=Lax` rather than forcing `SameSite=None; Secure` (which would break plain-HTTP
   * local dev).
   *
   * **Transport only, not a BFF.** No business logic, reshaping, or aggregation happens here;
   * that remains forbidden by PLAN §4.2. The rewrite must also stay non-buffering for the SSE
   * chat path (ADR 007) — verified in task 20.
   */
  async rewrites() {
    return [
      {
        source: '/api/v1/:path*',
        destination: `${backendInternalUrl}/api/v1/:path*`,
      },
    ];
  },
};

export default withSerwist(withNextIntl(nextConfig));
