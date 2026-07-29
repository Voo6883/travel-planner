# Task Status Ledger

> Deliverable of [Task 00](00-plan-baseline.md). Single source of truth for what is `done`.
> Baseline and dependency rationale: [`EXECUTION-BASELINE.md`](EXECUTION-BASELINE.md).

**Baseline commit:** `aa20043` · **Working branch:** `dev` · **Last updated:** 2026-07-29

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
| 00 | [Baseline and execution map](00-plan-baseline.md) | — | `done` | Commit `aa20043`. B-1 and B-2 resolved; open item B-3 does not affect this task's DoD. |
| 01 | [Prerequisite and root tooling](01-prerequisite-root-tooling.md) | 00 | `done` | Commit `a6dcb3c`. 27/27 assertions per platform locally; **CI green on `ubuntu-latest` and `windows-latest`** — first Linux execution. **F-7 closed.** |
| 02 | [Backend minimal scaffold](02-backend-minimal-scaffold.md) | 01 | `done` | Commit `daca342`. Spring Boot 3.5.3, Gradle 8.14.5 wrapper, Java 21 toolchain. 10/10 tests; health/ready/404 verified at runtime. |
| 03 | [Frontend minimal scaffold](03-frontend-minimal-scaffold.md) | 01 | `done` | Commit `48f1388`. Next 15.5.21 / React 19. lint+typecheck+build clean, 9/9 tests; 320 px no-overflow verified in-browser for `en` and `ms`. |
| 04 | [Docker runtime and orchestration](04-docker-runtime.md) | 01, 02, 03 | `done` | Commit `b83dd86`. All three services healthy; `ready` reports `database: UP`; volume survives restart; images non-root and secret-free. |
| 05 | [CI and repository workflow](05-ci-repository-workflow.md) | 01, 02, 03, 04 | `done` | Commits `1da85b5`, `d24c88c`. All five jobs green on GitHub Actions, including `docker build + smoke` asserting `"database":"UP"` in a clean runner. Actions bumped off the deprecated Node 20 runtime. |

## Phase 0B/0C — platform

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 06 | [OpenAPI and error platform](06-openapi-error-platform.md) | 02, 03, 04, 05 | `done` | Commit `0e295fd`. Contract + error catalog + pagination + `expected_version` + request-id. 53 backend / 26 frontend tests. Drift gate verified in both directions against a committed tree. Open questions: **F-10**, **F-11**. |
| 07 | [Database and domain foundation](07-database-domain-foundation.md) | 02, 04, 06 | `done` | Commit `717e077`. Migrations V1–V6, pure domain, MapStruct adapters, `@Version` per ADR 008, ADR 009 revocation columns. Unit build Docker-free; 22 Testcontainers tests pass. **Next free migration: `V7`.** Open: **F-13**, **F-14**, **F-15**. |
| 08 | [Local identity and JWT session](08-local-identity-jwt.md) | 06, 07 | `done` | Commit `801759c`. ADR 009 in full: 30-min access + rotating 14-day refresh, reuse detection, composite-keyed lockout, `logout-all`. Migration **V7**. **F-13 and F-14 closed.** New: **F-17**, **F-18**. |
| 09 | [Mailer and account lifecycle](09-mailer-account-lifecycle.md) | 08 | `done` | Commit `0148e81`. Migrations V8–V10. Mail sends only `afterCommit`; tokens hashed and single-use via a conditional UPDATE. Revocation on password change/reset/delete. |
| 10 | [Firebase and GitHub identity providers](10-external-identity-providers.md) | 08, 09 | `done` | Commit `2d6ef5f`. ADR 009 §4 pre-hijack takeover closed and proven by test. Firebase asserts `aud`+`sign_in_provider`; GitHub uses primary+verified only. No migration needed. |
| 11 | [Frontend platform and auth UI](11-frontend-platform-auth-ui.md) | 03, 06, 08, 09, 10 | `done` | Commit `1c04f94`. **Closes F-18** — ADR 006 same-origin proxy; without it tasks 08–10's auth was unreachable from a browser. |
| 12 | [Admin platform](12-admin-platform.md) | 07, 08, 09, 11 | `done` | Commit `3acc779`. Migration **V12**. Disable/reset terminate sessions, proven with a live cookie. Seed absent under `prod` (allow-list, not denylist). Review fixed a stale-write that silently undid revocation. |
| 13 | [PWA foundation](13-pwa-foundation.md) | 04, 11 | `done` | Commit `6a18a37`. `/api/v1/**` network-only, enforced per-rule **and** by ordering. Declined PLAN §4.2.11's `defaultCache` row — see **F-21**. |
| 14 | [AI provider platform](14-ai-provider-platform.md) | 06, 07, 09 | `done` | Commit `12b6b23`. Migration **V11**. `Flux<LlmEvent>` sealed union per ADR 007. Open: **F-22** (LLM stub not blocked in prod), **F-23** (Reactor in `domain/`). |
| 15 | [Architecture and quality gates](15-quality-gates.md) | 02–14 | `done` | Commit `bd6ca0d`, merged `95970f5`. Checkstyle 10.21.0, JaCoCo 0.8.12 (LINE 85 / BRANCH 70 over domain+application, measured 86.3 / 75.4), 8/8 ArchUnit rules, ESLint import boundaries proven to fail on probe violations, Prettier, Vitest thresholds, Actuator (4 exposed / 4 sensitive 404). Clean `./gradlew build` green with 15 tasks executed; frontend `format:check`+`lint`+`typecheck`+`test:coverage`+`build` all exit 0, 226 tests. Thresholds and exception process: [`docs/QUALITY-GATES.md`](../docs/QUALITY-GATES.md). Found and fixed **F-26**. New: **F-25**. |

## Phase 1 — knowledge, intake, chat, research, itinerary

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 16 | [Knowledge domain and schema](16-knowledge-domain-schema.md) | 07, 14, 15 | `not_started` | B-4 resolved (`cdbb698`) — coverage model, licence register, embedding lifecycle, HNSW now in scope. |
| 17 | [Knowledge seed and retrieval](17-knowledge-seed-retrieval.md) | 14, 16 | `not_started` | B-4 resolved (`020bd4a`) — knowledge stub is now the documented exception to §4.0.7; 3 curated destinations. |
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

## Knowledge operations — added by ADR 010

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 40 | [TKB refresh and re-embedding](40-tkb-refresh-reembed.md) | 14, 15, 16, 17 | `not_started` | Added `3d8bac3`. Required by ADR 010 Consequences; absent from the original 00–39. |
| 41 | [Admin knowledge curation](41-admin-knowledge-curation.md) | 12, 16, 17, 40 | `not_started` | Added `3ee39ab`. ADR 010 §7 — without it, fixing a closed restaurant needs a migration and redeploy. |

## Final integration

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 38 | [Full-system verification](38-full-system-verification.md) | 01–37, **40, 41** (or explicitly waived with rationale) | `not_started` | No `Validation` section. Dependency list predates 40/41. |
| 39 | [Production configuration and release readiness](39-release-readiness.md) | 38 | `not_started` | No `Validation` section. Requires 38 with no release-blocking failures. |

---

## Summary

| Status | Count |
|---|---|
| `done` | **16** |
| `in_progress` | 0 |
| `blocked` | 0 |
| `review` | 0 |
| `not_started` | 26 |

*42 tasks total — 40 original plus 40/41 added by ADR 010.*

## Milestone — Phase 0B platform complete (2026-07-28)

Tasks 00–14 are `done`. Tagged `v0.2.0-phase-0b`.

Everything a feature needs now exists: contract, database, identity, mail,
admin, frontend platform, PWA, and the AI runtime. Task 15 (quality gates) — the last of
Phase 0B — is implemented on `agent/task-15-quality-gates` and awaiting commit, PR and the
evidence gate. **Tasks 16 and 18 unblock once it is `done`.**

F-23 is no longer only a note: `LayerRulesTest.domainIsFrameworkFree` now permits `reactor..`
explicitly, so resolving F-23 means deleting one entry from that rule's allow-list.

Proven against a running Compose stack, through the ADR 006 same-origin proxy exactly as a
browser reaches it:

| Flow | Result |
|---|---|
| `register` | `202 PENDING_VERIFICATION` |
| `login` before verifying | `403 email_not_verified` |
| `verify-email/confirm` | `204` |
| `login` after verifying | `200` + session cookie |
| `/auth/me` | `200`, `roles ["USER"]`, `linked_providers ["LOCAL"]` |
| Seeded dev admin | `ADMIN` / `123456` → `200`, `roles ["ADMIN"]` |

**Open decisions carried into Phase 1** — none blocking, but **F-22 and F-23 should be settled
before Task 15** writes the ArchUnit ruleset:

| ID | Decision needed |
|---|---|
| **F-22** | The LLM stub is not blocked in production. ADR 010 §3 forbids a stub *knowledge* adapter in prod; nothing equivalent exists for the LLM, so a prod deploy with no key serves placeholder text |
| **F-23** | Reactor `Flux` now appears in `domain/` via `LlmPort`. Both PLAN §5.1 and ADR 007 put it there, but it is a third-party type in the layer that is meant to have none |
| **F-21** | `PLAN.md` §4.2.11's stack row names Serwist's `defaultCache`, which network-first caches same-origin `/api/`. Task 13 declined it; the row should be corrected |
| **F-17** | `PLAN.md` §4.0.5's endpoint table still calls `/auth/refresh` "optional v1.1" and omits `/auth/logout-all` |
| **F-19** | ADR 009 §6's "keyed on both" is ambiguous; task 08 used a composite key |
| **F-24** | `AGENTS.md` "Cursor Cloud" section still claims the repo is planning-only with no `package.json` and no `apps/frontend` |
| **F-25** | `components/layout/{app-shell,account-menu}.tsx` import `@/features/auth`. Shared chrome that renders identity — a real boundary violation, carved out in `eslint.config.mjs` rather than hidden. Fix by passing the user from a route-level provider, or move the shell into `features/auth` |
| **F-26** | Commit `038221d` added `spring-boot-devtools` with no version and no BOM on `developmentOnly`, breaking `./gradlew build` (and therefore CI) on `dev` from that commit until task 15 fixed it. Three commits on `dev` — `038221d`, `48ab038`, `228da7f` — are tracked by no task; the ledger cannot show breakage it does not know about |

**Active blockers:** none. **B-3** (`gh` unauthenticated) is informational only.
