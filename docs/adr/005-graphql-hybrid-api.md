# ADR 005: Hybrid GraphQL + REST API

## Status

Accepted

## Context

The plan originally locked **REST-only** client integration under `/api/v1/` with OpenAPI
as the single contract source (§6.1). Travel Planner screens load **nested graphs**
(trip + brief + research + itinerary + legs) and the trip stepper issues many correlated
reads. Multiple REST round-trips cause waterfall loading, duplicated DTO shapes, and
over-fetching.

GraphQL fits read-heavy, nested planner UIs. Some transports do **not** map cleanly to
GraphQL:

- OAuth redirects and cookie-based auth flows
- SSE chat streaming (`POST .../chat/messages`)
- Async job kickoff with `202 Accepted` + `job_id` (research agent)
- Health/readiness probes and Prometheus
- Booking confirm with `Idempotency-Key` header

## Decision

Adopt a **hybrid API**:

| Transport | Endpoint | Use for |
|---|---|---|
| **GraphQL** | `POST /graphql` | Client **queries** and **data mutations** — trips, brief, research results, itinerary, profile, admin lists |
| **REST** | `/api/v1/` | **Operational** endpoints — auth, SSE chat, async 202 commands, health, booking confirm |

Both transports delegate to the **same** `application/*Service` classes. Resolvers and
controllers are thin routing layers only (§4.0.1).

### Contract sources

| Contract | Location | Codegen target |
|---|---|---|
| GraphQL schema | `apps/backend/src/main/resources/graphql/schema.graphqls` | `apps/frontend/src/generated/graphql/` via GraphQL Code Generator |
| OpenAPI (REST-only) | `apps/backend/src/main/java/.../api/openapi/` | `apps/frontend/src/generated/rest/` for auth, chat, jobs, health |

**Rule:** Do not duplicate the same operation in both GraphQL and REST unless REST is
required for transport reasons (SSE, OAuth redirect, 202 async). Data CRUD moves to
GraphQL; REST paths for migrated reads are deprecated in OpenAPI with `deprecated: true`
until frontend migration completes.

### GraphQL stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot **`spring-boot-starter-graphql`** |
| Schema style | **Schema-first** — `.graphqls` files are source of truth |
| Resolver discipline | `@Controller` + `@QueryMapping` / `@MutationMapping` — ≤10 lines; delegate to service |
| Auth | Same JWT `tp_session` httpOnly cookie; GraphQL HTTP POST includes cookie |
| Errors | `GraphQlExceptionHandler` maps `DomainException` → `extensions.code` (same snake_case codes as REST §6.1) |
| N+1 | `@BatchMapping` or DataLoader per nested field (itinerary days → items → legs) |
| Frontend client | **GraphQL Code Generator** + TanStack Query wrappers in `lib/graphql/` |
| Introspection | Enabled in `dev`/`docker`; disabled in `prod` |

### REST endpoints that stay REST (non-negotiable)

| Category | Examples | Reason |
|---|---|---|
| Auth | `POST /auth/login`, `GET /auth/oauth/github/start`, `POST /auth/firebase` | Redirects, cookies, provider tokens |
| Chat SSE | `POST /planner/chat/messages`, `POST /trips/{id}/chat/messages` | Server-Sent Events streaming |
| Async jobs | `POST /trips/{id}/research/run` → `202` | Long-running agent; poll via GraphQL `researchJob` query |
| Health | `GET /api/v1/health`, `GET /api/v1/ready` | Infra probes |
| Booking confirm | `POST /trips/{id}/bookings/{id}/confirm` | `Idempotency-Key` header semantics |

## Rationale

| Factor | GraphQL for data | REST for operations |
|---|---|---|
| Nested trip dashboard | Single query `trip(id) { brief research { recommendations } itinerary { days { items legs } } }` | 5+ GET round-trips |
| Type safety | Schema + codegen; frontend selects fields | OpenAPI per-endpoint types |
| Chat streaming | Not suitable | SSE over HTTP POST |
| OAuth | Not suitable | Standard redirect flow |
| Tooling | GraphiQL in dev | OpenAPI UI for REST subset |

## Consequences

- Sprint 1 adds GraphQL bootstrap alongside OpenAPI (see [`BACKLOG.md`](../../plans/BACKLOG.md) S1-5b, S2-5b).
- CI **contract** stage validates GraphQL schema + runs both codegen passes (§15).
- Feature workflow §12.2 step 2 updates: schema + OpenAPI (REST-only paths).
- Frontend `lib/api/` splits: `lib/graphql/` for data; `lib/rest/` for auth/chat/jobs.
- ArchUnit adds rule: resolvers must not import `infrastructure/` or `ai/` directly.
- New features (C6+): GraphQL types + resolvers for data; REST only when transport requires it.

## Alternatives considered

- **REST-only** — rejected; too many round-trips for nested planner UI; harder to evolve field selection per screen.
- **GraphQL-only** — rejected; SSE chat, OAuth redirects, and idempotent booking confirm are awkward or non-standard in GraphQL.
- **BFF GraphQL in Next.js** — rejected; duplicates business routing; backend already owns services.
