import createNextIntlPlugin from 'next-intl/plugin';
import type { NextConfig } from 'next';

// Serwist (PWA) wrapping is added by tasks/13-pwa-foundation.md — ADR 005 requires it from
// Phase 0b, not here.
const withNextIntl = createNextIntlPlugin('./src/lib/i18n/request.ts');

/**
 * Where the Next.js **server** reaches Spring Boot (ADR 006). Server-only on purpose: it is not
 * `NEXT_PUBLIC_*`, so it never reaches the browser, and the browser never needs it — every call
 * from a page is same-origin and lands on the rewrite below.
 *
 * `http://backend:8080` inside Compose, `http://localhost:8080` when the frontend runs on the host.
 */
const backendInternalUrl = (process.env.BACKEND_INTERNAL_URL ?? 'http://localhost:8080').replace(
  /\/+$/,
  '',
);

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Emits a self-contained server bundle so the runtime image needs no node_modules
  // (docker/frontend/Dockerfile).
  output: 'standalone',
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

export default withNextIntl(nextConfig);
