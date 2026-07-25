# Travel Planner Implementation Task Plan

> Planning artifacts only. This branch does not implement application code.

## Baseline

- Repository: `Voo6883/travel-planner`
- Base branch: `master`
- Base commit: `50f5d01ae3751bd84e906c530d99175e9f84fda5`
- Product scope: implement the existing C1–C5 Travel Planner plan, including knowledge-grounded AI, chat-first planning, itinerary generation, booking safety, authentication, PWA, and runtime infrastructure.

## Authority order

1. `plans/superpower/PLAN.md` — architecture, data flow, product behavior.
2. `docs/UI-UX-DESIGN-SYSTEM.md` — visual tokens, responsive behavior, accessibility.
3. `docs/adr/` — accepted one-way decisions.
4. `plans/USE-CASES.md` — acceptance criteria.
5. `plans/BACKLOG.md` — delivery sequencing.
6. These task briefs — execution-sized decomposition. They may clarify scope but must not silently override the sources above.

When documents conflict, stop implementation, record the conflict, and resolve it through a documentation/ADR change before coding.

## Execution rules

- Execute tasks in dependency order, not merely numeric order.
- One task per branch and pull request unless a task explicitly permits a shared foundation branch.
- Start each task from the latest accepted `master`.
- Read `AGENTS.md` and `docs/AI-AGENT-WORKFLOW.md` before implementation.
- Keep external providers behind ports; ship realistic stubs when a live provider is not selected.
- Never place LLM or external HTTP calls inside a database transaction.
- Validate all LLM output before persistence or side effects.
- Never allow an LLM to confirm a booking.
- Finish every task with tests, validation commands, a concise implementation report, and explicit handoff notes.

## Task index

### Phase 0A — repository foundation

- [00 — Baseline and execution map](00-plan-baseline.md)
- [01 — Prerequisite and root tooling](01-prerequisite-root-tooling.md)
- [02 — Backend minimal scaffold](02-backend-minimal-scaffold.md)
- [03 — Frontend minimal scaffold](03-frontend-minimal-scaffold.md)
- [04 — Docker runtime and orchestration](04-docker-runtime.md)
- [05 — CI and repository workflow](05-ci-repository-workflow.md)

### Phase 0B/0C — platform

- [06 — OpenAPI and error platform](06-openapi-error-platform.md)
- [07 — Database and domain foundation](07-database-domain-foundation.md)
- [08 — Local identity and JWT session](08-local-identity-jwt.md)
- [09 — Mailer and account lifecycle](09-mailer-account-lifecycle.md)
- [10 — Firebase and GitHub identity providers](10-external-identity-providers.md)
- [11 — Frontend platform and auth UI](11-frontend-platform-auth-ui.md)
- [12 — Admin platform](12-admin-platform.md)
- [13 — PWA foundation](13-pwa-foundation.md)
- [14 — AI provider platform](14-ai-provider-platform.md)
- [15 — Architecture and quality gates](15-quality-gates.md)

### Phase 1 — knowledge, intake, chat, research, itinerary

- [16 — Knowledge domain and schema](16-knowledge-domain-schema.md)
- [17 — Knowledge seed and retrieval](17-knowledge-seed-retrieval.md)
- [18 — Trip and TripBrief core](18-trip-brief-core.md)
- [19 — LLM TripBrief extraction](19-llm-trip-brief-extraction.md)
- [20 — Conversation persistence and SSE](20-conversation-sse.md)
- [21 — Planner chat and trip creation](21-planner-chat-trip-creation.md)
- [22 — Trip chat intake tools](22-trip-chat-intake-tools.md)
- [23 — Research job platform](23-research-job-platform.md)
- [24 — Deterministic destination ranking](24-destination-ranking.md)
- [25 — Travel research agent](25-travel-research-agent.md)
- [26 — Research API and frontend](26-research-api-frontend.md)
- [27 — Research chat tools and evaluation](27-research-chat-evaluation.md)
- [28 — Itinerary domain and scheduling](28-itinerary-domain-scheduling.md)
- [29 — Route and mobility planning](29-route-mobility.md)
- [30 — Itinerary generation agent](30-itinerary-agent.md)
- [31 — Itinerary UI and chat editing](31-itinerary-ui-chat-editing.md)

### Phase 2 — booking and advanced runtime

- [32 — Booking quote domain and stub suppliers](32-booking-quotes-stubs.md)
- [33 — Booking confirmation safety](33-booking-confirmation-safety.md)
- [34 — Payment and live adapter slots](34-payment-live-adapters.md)
- [35 — Booking chat tools](35-booking-chat-tools.md)
- [36 — Chat security and rendering](36-chat-security-rendering.md)
- [37 — Semantic cache and Redis](37-semantic-cache-redis.md)

### Knowledge operations (added by ADR 010)

These two briefs were added after ADR 010 was accepted; its Consequences section requires them
and records that tasks 00–39 did not cover them. Both are numbered outside the original sequence
but execute within Phase 1, after Task 17.

- [40 — TKB refresh and re-embedding pipeline](40-tkb-refresh-reembed.md)
- [41 — Admin knowledge curation](41-admin-knowledge-curation.md)

### Final integration

- [38 — Full-system verification](38-full-system-verification.md)
- [39 — Production configuration and release readiness](39-release-readiness.md)

> Tasks 38 and 39 gate on 40 and 41 as well — "Tasks 01–37 complete" predates their existence.

## Standard task completion report

Every implementation PR must report:

1. Task ID and source commit.
2. Files and layers changed.
3. Architecture decisions or deviations.
4. Tests and commands run, including exact results.
5. Stubbed versus live integrations.
6. Known limitations.
7. Database migration and compatibility notes.
8. Handoff state for dependent tasks.
