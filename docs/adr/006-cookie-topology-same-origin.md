# ADR 006: Same-origin API via Next.js rewrite proxy

## Status

Accepted — **amends** the "frontend calls Spring Boot directly (no BFF)" note in
[ADR 002](002-jwt-auth.md) and `PLAN.md` §4.0.5. Session cookie semantics unchanged.

## Context

[ADR 002](002-jwt-auth.md) locks the session as an **httpOnly, Secure, SameSite=Lax** cookie and
states the frontend calls Spring Boot **directly**. `PLAN.md` §4.0.0.2 defines
`NEXT_PUBLIC_API_BASE_URL`, and Docker Compose runs the frontend on `:3000` and the backend on
`:8080`.

These are mutually incompatible. `SameSite=Lax` cookies are sent only on same-site requests and
top-level GET navigations. A cross-site `fetch(..., { credentials: 'include' })` from
`localhost:3000` to `localhost:8080` **will not carry `tp_session`**. Every authenticated request
fails — not intermittently, but from the first login attempt in Phase 0b.

Three secondary problems share the same root cause:

- **SSR base URL** — the browser needs `http://localhost:8080`; the Next.js server inside Compose
  needs `http://backend:8080`. Only one variable is defined.
- **CSRF** — `SameSite=None` would make the CSRF token mandatory rather than defence-in-depth.
- **`Secure` on localhost** — `SameSite=None` requires `Secure`, which breaks plain-HTTP local dev.

## Decision

**The browser only ever talks to the frontend origin.** Next.js proxies `/api/v1/**` to Spring
Boot via `rewrites()` in `next.config.ts`.

| Item | Choice |
|---|---|
| Browser API base | **`/api/v1`** — relative, same-origin (`NEXT_PUBLIC_API_BASE_URL=/api/v1`) |
| Proxy | Next.js `rewrites()` → `${BACKEND_INTERNAL_URL}/api/v1/:path*` |
| Server-side base | **`BACKEND_INTERNAL_URL`** — server-only, never `NEXT_PUBLIC_*` (`http://backend:8080` in Compose, `http://localhost:8080` on host) |
| Cookie | Unchanged — httpOnly, `SameSite=Lax`, `Secure` in prod |
| CORS | **Not required** for browser traffic; keep a strict allowlist for non-browser clients |
| CSRF | Required — `XSRF-TOKEN` cookie (readable) + `X-XSRF-TOKEN` header on all mutating requests, including the POST-SSE chat call |

**This is a transport proxy, not a BFF.** The rewrite is pass-through only. Any business logic,
response reshaping, or aggregation in `app/api/` remains forbidden (`PLAN.md` §4.2). This
distinction is what keeps ADR 002's "no BFF" intent intact while fixing the cookie.

### Streaming requirement

The proxy sits in the chat streaming path ([ADR 007](007-chat-streaming-transport.md)) and
**must not buffer**. Verify during Task 03/04 that a `text/event-stream` response flushes
incrementally through `rewrites()`. If buffering is observed, the fallback is a dedicated
Node-runtime route handler that pipes the upstream `ReadableStream` unmodified — still transport
only. Do not resolve this by moving chat to a different origin.

## Rationale

| Factor | Rewrite proxy (chosen) | `SameSite=None; Secure` | Subdomains + `Domain=` cookie |
|---|---|---|---|
| Works on plain-HTTP localhost | Yes | **No** — `Secure` required | Needs hosts-file setup |
| CSRF exposure | Lowest — `Lax` retained | Highest — cookie sent cross-site | Medium |
| CORS config | None needed | Preflight on every mutation | Needed |
| Solves SSR base URL | Yes — one server-only var | No | No |
| Deployment coupling | Frontend must reach backend | Independent | Requires shared parent domain |
| Reverses a locked decision | Transport only | No | No |

`SameSite=None` was the main contender. It is rejected because it requires `Secure` (breaking the
locked local-dev experience of `docker compose up` over plain HTTP), and because it converts CSRF
from defence-in-depth into the only protection on every mutating endpoint.

## Consequences

- `next.config.ts` gains a `rewrites()` entry; `BACKEND_INTERNAL_URL` is added to `.env.example`
  with a comment on the Compose-vs-host distinction (`PLAN.md` §4.0.0.2).
- `lib/api/client.ts` uses a **relative** base URL, sends `credentials: 'include'`, and attaches
  the CSRF header. It must **never** attempt to read or attach the JWT — the cookie is httpOnly.
  This supersedes the "attach session/JWT in `lib/api/client.ts`" wording in `PLAN.md` §4.2.
- Spring Security enables `CookieCsrfTokenRepository.withHttpOnlyFalse()`; the SPA obtains the
  initial token via a bootstrap `GET`.
- Serwist must treat `/api/v1/**` as network-only ([ADR 005](005-nextjs-pwa.md)) — now
  same-origin, so the service worker sees this traffic and a buffering handler would break
  streaming.
- The frontend container requires network reachability to the backend container.
- Tasks affected: **03** (frontend scaffold), **04** (Docker runtime), **08** (JWT session),
  **11** (frontend platform), **20** (SSE).

## Alternatives considered

- **`SameSite=None; Secure`** — rejected; breaks plain-HTTP local dev and maximises CSRF surface.
- **Subdomains (`app.` / `api.`) with `Domain=` cookie** — rejected for v1; requires DNS/hosts
  setup for local development and still leaves the SSR base-URL problem unsolved.
- **Full BFF in `app/api/`** — rejected; reverses a locked decision, duplicates DTOs, and
  contradicts OpenAPI-as-single-contract (`PLAN.md` §4.0).
- **Token in `Authorization` header instead of a cookie** — rejected by [ADR 002](002-jwt-auth.md)
  (XSS exposure via JS-readable storage).
