import createNextIntlPlugin from 'next-intl/plugin';
import type { NextConfig } from 'next';

// Serwist (PWA) wrapping is added by tasks/13-pwa-foundation.md — ADR 005 requires it from
// Phase 0b, not here.
const withNextIntl = createNextIntlPlugin('./src/lib/i18n/request.ts');

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
};

export default withNextIntl(nextConfig);
