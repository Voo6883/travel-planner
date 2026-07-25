# ADR 005: Next.js Progressive Web App (PWA)

## Status

Accepted

## Context

The frontend is locked as **Next.js App Router** (§1, §4.2). Travelers use the planner
on mobile as often as desktop. Without PWA support, “Add to Home Screen”, installability,
and an offline shell are unavailable. The plan previously did not state whether PWA was
allowed; agents and implementers need an explicit lock before Phase 0b scaffold.

## Decision

**Allow and require** a Progressive Web App for the Next.js frontend from Phase 0b.

| Item | Choice |
|---|---|
| Library | **Serwist** (`@serwist/next`) |
| Manifest | App Router `src/app/manifest.ts` |
| Service worker | `src/sw.ts` → build emits `public/sw.js` |
| Offline | Branded `/~offline` fallback page (app shell only) |
| API traffic | **Network-only** for `/api/v1/**` — do not cache auth responses |

Full rules: [`plans/superpower/PLAN.md`](../../plans/superpower/PLAN.md) §4.2.11.

### v1 scope

- Installable (manifest + icons + service worker in production builds).
- Precache static assets / app shell; offline navigation fallback.
- **Not** full offline trip planning, background sync, or push notifications.

## Rationale

| Factor | Serwist | next-pwa / forks | Hand-rolled Workbox |
|---|---|---|---|
| Next.js App Router | First-class `@serwist/next` | Legacy / less maintained | Manual glue |
| Maintenance | Active (Workbox successor path) | Stale relative to Serwist | Team owns all updates |
| Turbopack path | Documented (`@serwist/turbopack` when needed) | Often webpack-only | Custom |
| Complexity | Config wrapper + `sw.ts` | Similar | Highest |

## Consequences

- Sprint 2 story **S2-14** wires Serwist during frontend scaffold.
- `next.config.ts` wraps config with `withSerwist`; SW disabled in development.
- Pre-merge checklist and Phase 0 exit criteria include PWA build artifacts.
- Caching authenticated API bodies or JWTs in Cache Storage is **forbidden**.

## Alternatives considered

- **No PWA** — rejected; mobile installability is a v1 product goal.
- **`next-pwa` / `@ducanh2912/next-pwa`** — rejected; Serwist is the maintained successor.
- **Capacitor / React Native** — rejected for v1; web PWA only.
- **Full offline-first data** — deferred post-v1; requires sync design beyond Phase 0–2.
