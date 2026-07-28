import type { MetadataRoute } from 'next';

/**
 * Web App Manifest (PLAN §4.2.11, design system §9.2).
 *
 * Every value here is pinned by `docs/PLAN-COMPATIBILITY.md` ("PWA manifest colors/short_name vs
 * design system → Trips / `#0958D9` / `#F8FAFC`"). They are written as literals rather than read
 * from `design-tokens.ts` because the manifest is a static JSON document consumed by the
 * *operating system*, not by CSS: it has no notion of a theme variable, and it cannot follow the
 * light/dark switch the app itself supports.
 *
 * `background_color` is therefore the light canvas even for a user whose app is in dark mode.
 * That is the honest trade — the value paints the splash screen for the fraction of a second
 * before the no-flash bootstrap script in `layout.tsx` runs, and matching the light canvas is
 * what stops a *first* launch from flashing. The `theme-color` meta tag, which browsers do
 * re-read per mode, is where the dark surface is declared (see `layout.tsx`).
 *
 * Route-segment metadata is not localised: Next.js generates one `/manifest.webmanifest` at build
 * time, and next-intl resolves the locale per request from a cookie. Translating the app name in
 * both locales would need a locale-segmented manifest URL, which PLAN §4.2.3's route map does not
 * have. The names below are proper nouns in `en` and `ms` alike, so nothing is lost today; the
 * handoff note in `tasks/STATUS.md` records it for whoever adds locale-prefixed routes.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Travel Planner',
    short_name: 'Trips',
    description: 'Plan trips by chatting. Every recommendation is grounded in real travel knowledge.',
    start_url: '/',
    // `standalone`, not `fullscreen`: the planner is a content app and still needs the platform
    // status bar and the `env(safe-area-inset-*)` handling §4.4 relies on.
    display: 'standalone',
    orientation: 'portrait-primary',
    background_color: '#F8FAFC',
    theme_color: '#0958D9',
    icons: [
      { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
      // Kept as a separate asset rather than `purpose: 'any maskable'`: a single dual-purpose
      // icon forces the launcher to crop artwork drawn for an un-cropped tile (§9.2).
      { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  };
}
