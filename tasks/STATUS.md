# Task Status Ledger

> Deliverable of [Task 00](00-plan-baseline.md). Single source of truth for what is `done`.
> Baseline and dependency rationale: [`EXECUTION-BASELINE.md`](EXECUTION-BASELINE.md).

**Baseline commit:** `aa20043` · **Baseline branch:** `master` · **Last updated:** 2026-07-25

## Status values

| Status | Meaning |
|---|---|
| `not_started` | No branch, no work. Default. |
| `in_progress` | Branch exists, implementation underway. |
| `blocked` | Cannot proceed — a blocker ID is recorded in the Notes column. |
| `review` | Implementation complete, PR open, evidence gate not yet accepted. |
| `done` | Merged **and** the `docs/AGENT-HARNESS.md` §6 evidence gate passed with real command output. |

**A task may only start when every task in its `Depends on` column is `done`**
(`docs/AGENT-HARNESS.md` §2). Generated code alone never justifies `done`.

## Rules for updating this file

1. One status change per task, in the same PR that causes it.
2. Moving a task to `done` requires the evidence link (PR number or commit) in Notes.
3. Moving a task to `blocked` requires a blocker ID registered in
   [`EXECUTION-BASELINE.md`](EXECUTION-BASELINE.md) §7 — never a bare "blocked".

---

## Phase 0A — repository foundation

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 00 | [Baseline and execution map](00-plan-baseline.md) | — | `done` | Commit `aa20043`. Open items B-2, B-3 do not affect this task's DoD. |
| 01 | [Prerequisite and root tooling](01-prerequisite-root-tooling.md) | 00 | `not_started` | **Next executable task.** Not blocked by B-1 — it authors the gate rather than passing it. |
| 02 | [Backend minimal scaffold](02-backend-minimal-scaffold.md) | 01 | `not_started` | B-1 resolved — JDK 21.0.11 LTS installed. |
| 03 | [Frontend minimal scaffold](03-frontend-minimal-scaffold.md) | 01 | `not_started` | B-1 resolved — Node 22.23.1. May run in parallel with 02. |
| 04 | [Docker runtime and orchestration](04-docker-runtime.md) | 01, 02, 03 | `not_started` | B-1 resolved. |
| 05 | [CI and repository workflow](05-ci-repository-workflow.md) | 01, 02, 03, 04 | `not_started` | |

## Phase 0B/0C — platform

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 06 | [OpenAPI and error platform](06-openapi-error-platform.md) | 02, 03, 04, 05 | `not_started` | |
| 07 | [Database and domain foundation](07-database-domain-foundation.md) | 02, 04, 06 | `not_started` | |
| 08 | [Local identity and JWT session](08-local-identity-jwt.md) | 06, 07 | `not_started` | ADR 002 superseded by ADR 004/009 — see harness §7. |
| 09 | [Mailer and account lifecycle](09-mailer-account-lifecycle.md) | 08 | `not_started` | |
| 10 | [Firebase and GitHub identity providers](10-external-identity-providers.md) | 08, 09 | `not_started` | No `Validation` section — universal evidence gate applies. |
| 11 | [Frontend platform and auth UI](11-frontend-platform-auth-ui.md) | 03, 06, 08, 09, 10 | `not_started` | |
| 12 | [Admin platform](12-admin-platform.md) | 07, 08, 09, 11 | `not_started` | No `Validation` section. DB/JWT role = `ADMIN`, Spring = `ROLE_ADMIN`. |
| 13 | [PWA foundation](13-pwa-foundation.md) | 04, 11 | `not_started` | ADR 005 — Serwist required from Phase 0b. |
| 14 | [AI provider platform](14-ai-provider-platform.md) | 06, 07, 09 | `not_started` | |
| 15 | [Architecture and quality gates](15-quality-gates.md) | 02–14 | `not_started` | No `Validation` section. Sets coverage/arch thresholds — nothing before this may. |

## Phase 1 — knowledge, intake, chat, research, itinerary

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 16 | [Knowledge domain and schema](16-knowledge-domain-schema.md) | 07, 14, 15 | `not_started` | No `Validation` section. |
| 17 | [Knowledge seed and retrieval](17-knowledge-seed-retrieval.md) | 14, 16 | `not_started` | ADR 010 — no stub Knowledge adapter in prod. |
| 18 | [Trip and TripBrief core](18-trip-brief-core.md) | 06, 07, 11, 15, 17 | `not_started` | No `Validation` section. |
| 19 | [LLM TripBrief extraction](19-llm-trip-brief-extraction.md) | 14, 18 | `not_started` | LLM output → schema/golden-file test required. |
| 20 | [Conversation persistence and SSE](20-conversation-sse.md) | 06, 07, 11, 14, 15 | `not_started` | No `Validation` section. ADR 007 — `Flux<LlmEvent>`. |
| 21 | [Planner chat and trip creation](21-planner-chat-trip-creation.md) | 18, 19, 20 | `not_started` | No `Validation` section. **First end-to-end product loop closes here.** |
| 22 | [Trip chat intake tools](22-trip-chat-intake-tools.md) | 18, 19, 20, 21 | `not_started` | No `Validation` section. Tool args must be schema-validated. |
| 23 | [Research job platform](23-research-job-platform.md) | 07, 18, 22 | `not_started` | |
| 24 | [Deterministic destination ranking](24-destination-ranking.md) | 17, 18, 23 | `not_started` | No `Validation` section. LLM must not overwrite numeric fit score. |
| 25 | [Travel research agent](25-travel-research-agent.md) | 14, 17, 23, 24 | `not_started` | No `Validation` section. |
| 26 | [Research API and frontend](26-research-api-frontend.md) | 11, 23, 24, 25 | `not_started` | No `Validation` section. `ranked-recommendations` is `GET`. |
| 27 | [Research chat tools and evaluation](27-research-chat-evaluation.md) | 22, 25, 26 | `not_started` | No `Validation` section. Eval harness = backlog S4-9. |
| 28 | [Itinerary domain and scheduling](28-itinerary-domain-scheduling.md) | 17, 18, 26 | `not_started` | No `Validation` section. |
| 29 | [Route and mobility planning](29-route-mobility.md) | 17, 28 | `not_started` | No `Validation` section. |
| 30 | [Itinerary generation agent](30-itinerary-agent.md) | 14, 26, 28, 29 | `not_started` | No `Validation` section. |
| 31 | [Itinerary UI and chat editing](31-itinerary-ui-chat-editing.md) | 22, 27, 30 | `not_started` | No `Validation` section. `patch_itinerary` = tool name; HTTP `PATCH` forbidden. |

## Phase 2 — booking and advanced runtime

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 32 | [Booking quote domain and stub suppliers](32-booking-quotes-stubs.md) | 07, 15, 31 | `not_started` | No `Validation` section. |
| 33 | [Booking confirmation safety](33-booking-confirmation-safety.md) | 32 | `not_started` | No `Validation` section. **An LLM may never confirm a booking.** |
| 34 | [Payment and live adapter slots](34-payment-live-adapters.md) | 32, 33 | `not_started` | |
| 35 | [Booking chat tools](35-booking-chat-tools.md) | 27, 31, 32, 33 | `not_started` | No `Validation` section. |
| 36 | [Chat security and rendering](36-chat-security-rendering.md) | 20, 21, 22, 27, 31, 35 | `not_started` | No `Validation` section. |
| 37 | [Semantic cache and Redis](37-semantic-cache-redis.md) | 14, 17, 25, 30, 35, 36 | `not_started` | No `Validation` section. New runtime service — confirm against plan before starting. |

## Final integration

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 38 | [Full-system verification](38-full-system-verification.md) | 01–37 (or explicitly waived with rationale) | `not_started` | No `Validation` section. |
| 39 | [Production configuration and release readiness](39-release-readiness.md) | 38 | `not_started` | No `Validation` section. Requires 38 with no release-blocking failures. |

---

## Summary

| Status | Count |
|---|---|
| `done` | 1 |
| `in_progress` | 0 |
| `blocked` | 0 |
| `review` | 0 |
| `not_started` | 39 |

**Active blockers:** B-3 (`gh` unauthenticated) only, and it does not gate execution.

Resolved 2026-07-25: **B-1** (toolchain) — Node 22.23.1 and Temurin JDK 21.0.11 LTS verified.
**B-2** (trunk) — `master` is the trunk; branch from and merge to `master`, `main` is abandoned.
Full detail in [`EXECUTION-BASELINE.md`](EXECUTION-BASELINE.md) §7.
