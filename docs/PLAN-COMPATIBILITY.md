# Plan compatibility review (post-merge master)

> Reviewed on **2026-07-25** after plan branches merged into `master`.
> Scope: documentation / locked decisions only (no application scaffold yet).

## Merged branches covered

| Branch | Outcome on master |
|---|---|
| `cursor/ui-design-theme-9aec` | UI/UX design system + authority split |
| `cursor/architecture-diagrams-1514` | System / flow / activity diagrams |
| `cursor/setup-dev-environment-5550` | Cursor Cloud notes in `AGENTS.md` |
| `cursor/graphql-hybrid-api-1514` | Explored then **reverted** — REST+OpenAPI remains locked |
| Earlier locks (PWA, apps folders, single `.env`, chat-first, TKB, …) | Already on master |

Remote tip branches with no commits ahead of `master` are fully absorbed.

## Authority (compatible)

| Concern | Wins |
|---|---|
| Architecture, data flow, product behavior | `plans/superpower/PLAN.md` |
| Exact UI tokens, responsive, a11y, PWA presentation | `docs/UI-UX-DESIGN-SYSTEM.md` |
| Locked one-way decisions | `docs/adr/*` |
| Acceptance criteria | `plans/USE-CASES.md` |
| Delivery sequencing | `plans/BACKLOG.md` |

## Compatible (aligned after review)

- Monorepo: `apps/frontend` (Node 22) + `apps/backend` (Java 21)
- Single root `.env` / `.env.example`
- REST `/api/v1/` + OpenAPI codegen — **no GraphQL**
- Auth: local + Firebase Gmail + GitHub; JWT cookie; Resend mailer
- PWA: Serwist required from Phase 0b (ADR 005)
- Chat-first + status-gated tools; knowledge-based AI (TKB + RAG)
- Stub-first external adapters
- Trip status enum shared across USE-CASES, UI, diagrams (incl. `ARCHIVED`)

## Fixes applied in this review

| Severity | Issue | Resolution |
|---|---|---|
| Must | `POST` examples for `ranked-recommendations` vs locked `GET` | PLAN + workflow examples → `GET` |
| Must | Admin role `ADMIN` vs `ROLE_ADMIN` ambiguity | Document DB/JWT=`ADMIN`, Spring=`ROLE_ADMIN`, `hasRole('ADMIN')` |
| Must | PWA manifest colors/short_name vs design system | PLAN sketch → Trips / `#0958D9` / `#F8FAFC` |
| Must | API cache “network-first” vs ADR network-only | PLAN → **network-only** for `/api/v1/**` |
| Must | Auth P1 phase (PLAN Phase 1 vs Backlog 0b) | PLAN + USE-CASES → Phase **0b** / S1-6e |
| Must | C5 only in Sprint 8 vs chat in Sprint 3+ | PLAN roadmap: C5 spans Phase 1–2 |
| Should | Research “failed trip status” in diagrams | Fail on `research_job`; trip keeps last valid status |
| Should | Stale AGENTS “only README+PLAN” claim | Index all current plan docs |
| Should | Bad UC id `UC-C2-14`; risk → S4-6 | Backlog → `UC-C2-16`; eval harness **S4-9** |
| Should | UI PWA “needs separate decision” | Point to ADR 005 |
| Should | Diagrams missing PWA / `/api/v1` chat paths | Updated container + chat flows |
| Clarify | `patch_itinerary` tool vs HTTP no-PATCH | Explicit note: tool name ≠ HTTP verb |
| Clarify | Host vs Docker JDBC URL | Comment in `.env.example` |

## Remaining non-blocking notes

1. **Host `.env`:** root `.env` is locked; JDBC host must switch `postgres` → `localhost` when the backend runs on the host.
2. **Optional Next.js route handlers:** PLAN still allows thin BFF-style health proxies; primary API remains direct Spring Boot.
3. **Booking vs trip status:** always namespace as `booking.status` vs `trip.status` (Backlog S6-1 now explicit).

## Kickoff readiness

Documentation set is **plan-compatible for Phase 0a** once this review lands.
Next implementation work: Sprint 0 scaffold per `plans/BACKLOG.md` (not started).
