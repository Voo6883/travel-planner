# ADR 002: Self-issued JWT in httpOnly cookie for v1 auth

## Status

Accepted

## Context

User-based accounts are locked (§4.0.5) with no multi-tenant model. The auth *provider*
was undecided: self-issued JWT vs OAuth (Google/GitHub). Frontend calls Spring Boot
directly (no BFF).

## Decision

**v1 authentication:**

- `POST /api/v1/auth/login` — validate credentials, issue **self-signed JWT**
- JWT stored in **httpOnly, Secure, SameSite=Lax** cookie (name: `tp_session` or similar)
- `POST /api/v1/auth/logout` — clear cookie
- `GET /api/v1/auth/me` — return current user + roles for `useUserContext()`
- Frontend `lib/api/client.ts` uses `credentials: 'include'`

**OAuth (Google/GitHub)** is deferred to post-v1; add as supplementary login, not a replacement.

## Rationale

| Factor | JWT cookie | OAuth-only |
|---|---|---|
| Phase 0 complexity | Low — Spring Security standard | Higher — redirect flows, provider config |
| Admin seed (`ADMIN`) | Works immediately | Still needs local account story |
| SPA/Next.js direct API | Cookie + CORS config is well understood | Same, plus provider keys |
| Mobile/API clients later | Can add Bearer header variant | OAuth still needed |

## Security notes

- Short access token TTL (e.g. 15–60 min) with refresh strategy in v1.1 if needed
- `Secure` flag required in production; relaxed in local docker only
- BCrypt password hashing; never store plain passwords
- CSRF: SameSite=Lax + Spring Security CSRF for cookie-based auth if required by audit

## Consequences

- CORS must allow credentials from frontend origin (explicit origins per env — no wildcard in prod)
- OpenAPI documents cookie-based auth scheme
- OAuth addition later requires new ADR; does not change `UserContext` shape

## Alternatives considered

- **JWT in Authorization header only** — rejected for browser XSS exposure (localStorage)
- **Session server-side only** — acceptable but adds Redis dependency in Phase 0; cookie JWT is stateless enough for v1
