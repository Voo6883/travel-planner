# Task 13 — PWA Foundation

## Objective

Make the Next.js planner installable through Serwist while keeping authenticated and API data safe and fresh.

## Dependencies

- Task 11 complete.
- Task 04 Docker/build paths stable.

## Required reading

- `docs/adr/005-nextjs-pwa.md`
- `plans/superpower/PLAN.md` §4.2.11
- `docs/UI-UX-DESIGN-SYSTEM.md` PWA/offline guidance
- `plans/BACKLOG.md` S2-14

## Scope

- Install/configure `@serwist/next`.
- `src/sw.ts`, `app/manifest.ts`, required icons including maskable assets, and `/~offline` fallback.
- Manifest names and colors aligned with the accepted design tokens.
- Network-only strategy for `/api/v1/**`, auth, chat/SSE, and booking actions.
- Safe caching for versioned static assets, fonts, and public shell resources.
- Explicit offline UI that does not show stale actions as available.
- Update behavior and service-worker lifecycle handling.
- Production build verification and installability checks.
- Tests/config assertions preventing accidental API caching.

## Do not

- Do not cache JWT responses, API bodies, SSE, private trip data, booking/payment flows, or provider callbacks.
- Do not claim full offline trip planning.
- Do not add background sync for mutations without a new ADR.

## Validation

```bash
cd apps/frontend
npm run build
npm run test
```

Run Lighthouse/installability checks against a production build and inspect service-worker routes. Verify `/api/v1/**` fails normally offline rather than returning cached data.

## Definition of Done

- App is installable.
- Offline shell is clear and accessible.
- API/auth/private data is network-only.
- Service-worker update behavior is documented and tested.

## Handoff

Record cache rules, generated files, icon sources, validation method, and restrictions for future features.

## Suggested branch and commit

- Branch: `agent/task-13-pwa-foundation`
- Commit: `feat: add Serwist PWA foundation`
