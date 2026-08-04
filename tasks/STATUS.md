# Task Status Ledger

> Deliverable of [Task 00](00-plan-baseline.md). Single source of truth for what is `done`.
> Baseline and dependency rationale: [`EXECUTION-BASELINE.md`](EXECUTION-BASELINE.md).

**Baseline commit:** `aa20043` · **Working branch:** `dev` · **Last updated:** 2026-08-04

> **The Testcontainers suite now runs without Docker** — `npm run test:integration` points it at a
> locally-installed PostgreSQL, creating and dropping a throwaway database per run. Its first
> execution on this machine found **three defects that had been invisible for weeks** (F-41, F-42,
> F-43), two of them dating to tasks 09 and 16. Current state: **114/114 integration tests pass**
> against PostgreSQL 16.6 + pgvector 0.8.1. Task 16's Testcontainers waiver (**F-32**) no longer
> needs to stand on `ddl-auto: validate` alone.

## Status values

| Status | Meaning |
|---|---|
| `not_started` | No branch, no work. Default. |
| `in_progress` | Branch exists, implementation underway. |
| `blocked` | Cannot proceed — a blocker ID is recorded in the Notes column. |
| `review` | Implementation complete, PR open, evidence gate not yet accepted. |
| `done` | Merged **and** the `docs/AGENT-HARNESS.md` §6 evidence gate passed with real command output. |
| `done_with_accepted_debt` | Merged and gated, but a named follow-up was accepted by the owner. **Requires** an `F-NN` in Notes and a task that owns closing it. Task 16 is the case this exists for. |

**A task may only start when every task in its `Depends on` column is `done`, or when the specific
capability it needs is a `done` capability gate of an `in_progress` dependency** (`docs/AGENT-HARNESS.md`
§2). Generated code alone never justifies `done`.

### Capability gates

Tasks 18, 19 and 20 each started while a dependency was still `in_progress`. In all three cases the
reason was sound — the *capability* being depended on was finished even though the task was not — and
in all three cases the justification was written afterwards, as an exception to a rule. The 2026-07-29
review was right that exceptions are the wrong mechanism: a rule broken three times for good reasons
is a rule stated at the wrong granularity.

So a task whose scope splits into independently consumable capabilities declares them, and a dependent
task depends on the **gate** rather than on the whole task:

| Task | Gate | Capability it publishes | Status |
|---|---|---|---|
| 17 | **17A** | Seed contract + loader + SAMPLE dataset + `GET /destinations/supported` | `done` |
| 17 | **17B** | Hybrid retrieval — vector + `tsvector` fusion, shared adapter contract tests | `done` — fusion + **F-46**; adapter contract tests (**F-32**); seed validator (**F-44**) |
| 17 | **17C** | Real curation for the first three destinations (**F-34**) | `deferred` — accepted debt on task 17; owned by **41** |
| 18 | **18A** | Trip + brief backend: CRUD, clarification, `expected_version`, migration V20 | `done` |
| 18 | **18B** | Frontend brief editor + `locales/*/trip_brief.json` + `surprise_me` persistence | `done` — `features/intake` (list, editor, clarification, autosave, 409 merge) + en/ms locales; `surprise_me` column via **V23** |
| 19 | **19A** | Versioned prompt, repair retry, fallback, golden fixtures, injection separation | `done` |
| 19 | **19B** | `surprise_me` field wiring (needs **18B**'s migration) | `done` — on `TripBriefDetails`, `TripBrief`, entity, OpenAPI and the extraction save path |
| 20 | **20A** | Persistence: `planner_session`, `conversation`, `message`, `seq`, idempotency | `done` |
| 20 | **20B** | SSE transport: POST/GET streams, typed events, partial status, cursor by `seq` | `done` |
| 20 | **20C** | `Last-Event-ID` frame replay, **or** an ADR 007 amendment dropping the promise (**F-39**) | `done` — both: replay at message granularity, and ADR 007 amended to match |

A gate reaches `done` under the same evidence rule as a task: real command output, and CI green on the
PR that landed it. The gate table is what a dependent task cites — "21 depends on 18A, 19A, 20A, 20B"
is checkable, where "18 is close enough" is a judgement call made by whoever is in a hurry.

## Rules for updating this file

1. One status change per task, in the same PR that causes it.
2. Moving a task to `done` requires the evidence link (PR number or commit) in Notes.
3. Moving a task to `blocked` requires a blocker ID registered in
   [`EXECUTION-BASELINE.md`](EXECUTION-BASELINE.md) §7 — never a bare "blocked".
4. Moving a task to `done_with_accepted_debt` requires an `F-NN` in Notes **and** the task that owns
   closing it. Debt with no owner is a `done` that means nothing.
5. Starting a task against a capability gate rather than a whole dependency requires the gate to be
   listed as `done` in the table above, cited in the starting task's Notes. This replaces the practice
   of starting first and recording the deviation afterwards.

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
| 14 | [AI provider platform](14-ai-provider-platform.md) | 06, 07, 09 | `done` | Commit `12b6b23`. Migration **V11**. `Flux<LlmEvent>` sealed union per ADR 007. **Provider replay added** 2026-07-30 (review §6.I): `ai/replay/` records real provider output as committed fixtures keyed by prompt hash and replays them offline, so tests exercise real protocol shapes the stub cannot produce. An unrecorded prompt is an error, never a stub fallback. Both replay and recording refused under `prod`. **F-22 and F-23 both closed** 2026-07-30 — F-23 by moving the streaming turn to `application/ai/LlmStreamPort`, leaving `domain/port/LlmPort` framework-free. **F-22 closed** 2026-07-30 — `AiConfigValidator` refuses `stub` as chat *and* embedding provider under `prod`, and the missing-key message no longer offers the stub as the remedy. Also closed on this pass: token usage was recorded as zero for every non-streaming call (the router read usage off a `String`), and `ai_call_log.model` fell back to `""` so every cost estimate was zero. |
| 15 | [Architecture and quality gates](15-quality-gates.md) | 02–14 | `done` | Commit `bd6ca0d`, merged `95970f5`. Checkstyle 10.21.0, JaCoCo 0.8.12 (LINE 85 / BRANCH 70 over domain+application, measured 86.3 / 75.4), 8/8 ArchUnit rules, ESLint import boundaries proven to fail on probe violations, Prettier, Vitest thresholds, Actuator (4 exposed / 4 sensitive 404). Clean `./gradlew build` green with 15 tasks executed; frontend `format:check`+`lint`+`typecheck`+`test:coverage`+`build` all exit 0, 226 tests. Thresholds and exception process: [`docs/QUALITY-GATES.md`](../docs/QUALITY-GATES.md). Found and fixed **F-26**. **F-25 closed** 2026-07-30 — the shared-chrome import of `features/auth` is inverted and the ESLint carve-out is gone. |

## Phase 1 — knowledge, intake, chat, research, itinerary

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 16 | [Knowledge domain and schema](16-knowledge-domain-schema.md) | 07, 14, 15 | `done` | Commits `c68ff6f` (schema), `bbdc065` (domain). Migrations **V13–V18** applied against real Postgres 16.6 + pgvector 0.8.1: 12 tables, 6 partial HNSW indexes, and each guard proven to reject a bad write. Pure domain + `KnowledgePort` + typed `destination_not_covered` (registered end to end). 212 domain tests; coverage LINE 88.87% / BRANCH 81.73%. Persistence `8db9029`: 10 entities, 10 repositories, 10 mappers, adapter, native pgvector search. Handoff: [`docs/KNOWLEDGE-SCHEMA.md`](../docs/KNOWLEDGE-SCHEMA.md). **Testcontainers waived by the owner**; entity/schema alignment proven instead by `ddl-auto: validate` booting against Postgres 16.6 — see F-32. **Next free migration: derive it with `npm run code-map`** — a figure written in prose goes stale the moment two branches read it on different days. **F-27, F-28 and F-29 closed** 2026-07-30: `DestinationArea` range-checks its coordinates, `KnowledgeQuery` has value equality over its `float[]` (task 37's cache would otherwise miss on every lookup), and `Destination.timezone` is validated against the JVM's tzdb. Open: **F-30**, **F-33**. |
| 17 | [Knowledge seed and retrieval](17-knowledge-seed-retrieval.md) | 14, 16 | `done_with_accepted_debt` | Gates **17A** + **17B** closed with evidence (hybrid RRF, V21/V22, **F-44** seed validator, **F-32** adapter ITs, **F-46** fulltext). Gate **17C** deferred: SAMPLE dataset stays PARTIAL below ADR 010 §1 floor — real Tokyo/Bangkok/Shanghai curation is **F-34**, owned by task **41** (admin knowledge curation). Inventing FULL rows here would fabricate `source_refs` and violate ADR 010 §1/§3. |
| 18 | [Trip and TripBrief core](18-trip-brief-core.md) | 06, 07, 11, 15, 17 | `done` | Gates **18A** + **18B**. Merged `0c6f343` (intake UI + **V23** `surprise_me`). CI green on `dev` push `30877086309`. **F-35** remains (plan docs still name PUT clarification). |
| 19 | [LLM TripBrief extraction](19-llm-trip-brief-extraction.md) | 14, 18 | `done` | Gates **19A** + **19B**. `surprise_me` on details/entity/OpenAPI/extraction save path via merge `0c6f343`. CI green `30877086309`. |
| 20 | [Conversation persistence and SSE](20-conversation-sse.md) | 06, 07, 11, 14, 15 | `done` | Gates **20A** + **20B** + **20C**. Replay + ADR 007 amend (**F-39**) landed `8f2890a`. **F-31**/**F-36**/**F-40** (wire roles) closed earlier. |
| 21 | [Planner chat and trip creation](21-planner-chat-trip-creation.md) | 18, 19, 20 | `done` | Merged `0c6f343` — `PlannerTools`/`create_trip`/`CreateTripHandoffService`/`PlannerHomePanel`/`trip_created` navigation. Handoff: [`docs/PLANNER-CHAT-HANDOFF.md`](../docs/PLANNER-CHAT-HANDOFF.md). CI green `30877086309`. |
| 22 | [Trip chat intake tools](22-trip-chat-intake-tools.md) | 18, 19, 20, 21 | `done` | Merged `1286ae1` (PR [#20](https://github.com/Voo6883/travel-planner/pull/20)). `TripChatOrchestrator` + status-gated `update_trip_brief` / `answer_clarification`; SSE `brief_updated` invalidates brief form query. Handoff: [`docs/TRIP-CHAT-HANDOFF.md`](../docs/TRIP-CHAT-HANDOFF.md). CI green run `30882022128` (+ follow-up code-map refresh). |
| 23 | [Research job platform](23-research-job-platform.md) | 07, 18, 22 | `done` | Merged `4b0f6fa` (PR [#21](https://github.com/Voo6883/travel-planner/pull/21)). Migration **V24** `research_job`. Durable slice `application/research/` (202 start, poll, local executor, 90s timeout, stale reconciler, handler/completion hooks for task 25). On fail: job `FAILED`+`error_code`, trip → `BRIEF_COMPLETE`. Handoff: [`docs/RESEARCH-JOB-HANDOFF.md`](../docs/RESEARCH-JOB-HANDOFF.md). CI green on PR before merge. |
| 24 | [Deterministic destination ranking](24-destination-ranking.md) | 17, 18, 23 | `done` | Merged `e7f1816`. Pure `domain/algorithm/ranking/` (`DestinationRanker`, weights, exclusions, top-K). Deps: gates **17A/17B** + 17 `done_with_accepted_debt` / 18+23 `done`. Handoff: [`docs/DESTINATION-RANKING-HANDOFF.md`](../docs/DESTINATION-RANKING-HANDOFF.md). Local `./gradlew build -x integrationTest` green after checkstyle UnusedImports fix. |
| 25 | [Travel research agent](25-travel-research-agent.md) | 14, 17, 23, 24 | `done` | Merged to `dev`. Migration **V25**. Stub agent + KnowledgePort tools + completion-hook persistence. Handoff: [`docs/TRAVEL-RESEARCH-AGENT-HANDOFF.md`](../docs/TRAVEL-RESEARCH-AGENT-HANDOFF.md). Sample PARTIAL KB → typed `no_confident_result`. |
| 26 | [Research API and frontend](26-research-api-frontend.md) | 11, 23, 24, 25 | `done` | Merged to `dev`. Ranked list + select + guide APIs; research UI; handoff [`docs/RESEARCH-EXPERIENCE-HANDOFF.md`](../docs/RESEARCH-EXPERIENCE-HANDOFF.md). |
| 27 | [Research chat tools and evaluation](27-research-chat-evaluation.md) | 22, 25, 26 | `done` | Merged to `dev`. C2 chat tools + SSE invalidation; V26 completion mail; eval harness. Handoff: [`docs/RESEARCH-CHAT-EVAL-HANDOFF.md`](../docs/RESEARCH-CHAT-EVAL-HANDOFF.md). |
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
| 41 | [Admin knowledge curation](41-admin-knowledge-curation.md) | 12, 16, 17, 40 | `not_started` | Added `3ee39ab`. ADR 010 §7. **Owns F-34 / gate 17C** — first real Tokyo/Bangkok/Shanghai curation (and ongoing KB edits). |

## Final integration

| ID | Task | Depends on | Status | Notes |
|---|---|---|---|---|
| 38 | [Full-system verification](38-full-system-verification.md) | 01–37, **40, 41** (or explicitly waived with rationale) | `not_started` | No `Validation` section. Dependency list predates 40/41. |
| 39 | [Production configuration and release readiness](39-release-readiness.md) | 38 | `not_started` | No `Validation` section. Requires 38 with no release-blocking failures. |

---

## Summary

| Status | Count |
|---|---|
| `done` | **27** |
| `done_with_accepted_debt` | 1 |
| `in_progress` | 0 |
| `blocked` | 0 |
| `review` | 0 |
| `not_started` | 14 |

*42 tasks total — 40 original plus 40/41 added by ADR 010.*

## Milestone — Phase 0B platform complete (2026-07-28)

Tasks 00–14 are `done`. Tagged `v0.2.0-phase-0b`.

Everything a feature needs now exists: contract, database, identity, mail,
admin, frontend platform, PWA, and the AI runtime. Task 15 (quality gates) closed Phase 0B and
merged as `95970f5`, which unblocked tasks 16 and 20.

F-23 is closed: `LayerRulesTest.domainIsFrameworkFree` now **forbids** `reactor..`, and the rule
passes. The domain has no framework import of any kind, which is the property it existed to have.

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

**Open decisions carried into Phase 1** — **F-22 and F-23 are both closed** (2026-07-30). Historical note: they were flagged as things that should be settled
before Task 15** writes the ArchUnit ruleset:

| ID | Decision needed |
|---|---|
| **F-22** | The LLM stub is not blocked in production. ADR 010 §3 forbids a stub *knowledge* adapter in prod; nothing equivalent exists for the LLM, so a prod deploy with no key serves placeholder text |
| **F-23** | ✅ **CLOSED** 2026-07-30. The streaming turn moved from `domain/port/LlmPort` to `application/ai/LlmStreamPort`, so the domain imports no framework at all and `reactor..` is now on `domainIsFrameworkFree`'s forbidden list rather than absent from it. Interface segregation rather than relocation: `ChatTurnService` streams and never completes, `StructuredOutputRunner` completes and never streams, so neither lost anything. Adapters implement both through `ai/client/LlmProvider`, and `AiConfig` publishes one bean injectable as either port |
| **F-21** | `PLAN.md` §4.2.11's stack row names Serwist's `defaultCache`, which network-first caches same-origin `/api/`. Task 13 declined it; the row should be corrected |
| **F-17** | `PLAN.md` §4.0.5's endpoint table still calls `/auth/refresh` "optional v1.1" and omits `/auth/logout-all` |
| **F-19** | ADR 009 §6's "keyed on both" is ambiguous; task 08 used a composite key |
| **F-24** | `AGENTS.md` "Cursor Cloud" section still claims the repo is planning-only with no `package.json` and no `apps/frontend` |
| **F-25** | ✅ **CLOSED** 2026-07-30. `AppShell` and `AccountMenu` take identity as props; `features/auth/components/authenticated-app-shell.tsx` supplies it and is what the two route layouts render. The `eslint.config.mjs` carve-out for `src/components/layout/**` is **deleted** rather than narrowed — a scoped exception with a plausible justification is a precedent the next identity-rendering component points at. Proven by probe: re-adding the import fails `npm run lint`. `app-shell.test.tsx` renders the shell with no QueryClient at all, so re-introducing the hook fails immediately |
| **F-26** | Commit `038221d` added `spring-boot-devtools` with no version and no BOM on `developmentOnly`, breaking `./gradlew build` (and therefore CI) on `dev` from that commit until task 15 fixed it. Three commits on `dev` — `038221d`, `48ab038`, `228da7f` — are tracked by no task; the ledger cannot show breakage it does not know about |
| **F-27** | `DestinationArea` enforces the lat/long pairing rule but does **not** range-check coordinates, unlike `Destination` and `Poi`. Matches V14, which also has only the pairing constraint. Areas are what C3 schedules against geographically, so a nonsense coordinate is least likely to be noticed here — fix the record and V14 together |
| **F-28** | `KnowledgeQuery.equals`/`hashCode` are identity-based on the `float[] embedding` component, so two queries built from identical inputs are never equal. Normal for records with array components, but this type is documented as a value object and takes care to be immutable, so the gap is surprising. Matters if a query is ever used as a cache key (task 37) |
| **F-29** | `Destination` validates neither `name` nor `timezone` for blankness, while `DestinationArea`, `Poi`, `TransportMode` and `TravelApp` all reject a blank name. `timezone` is never checked as a valid IANA zone despite the javadoc requiring one before an itinerary can place an event on a clock (task 28 depends on this) |
| **F-30** | `DestinationNotCoveredException.supportedSlugs` is `transient`, so it deserialises to `null` although `supportedSlugs()` documents no null contract. Mirrors `DomainException.details`, so it may be deliberate — decide and document, or drop `transient` |

| ~~**F-31**~~ | **Resolved (task 20).** Settled in the DTO layer: `api/dto/chat/ChatWireNames` is the one place a chat enum becomes a wire value, Java stays `UPPER_SNAKE` (they are the persisted values and two CHECK constraints), and the wire is lower-case snake. Scoped to chat only — `roles` and `linked_providers` stay upper-case. `ChatWireNamesTest` asserts it for every constant of both enums, so a constant added later is published correctly by default. Both client-side `toLowerCase()` normalisations are deleted; the four-statuses-onto-two collapse stays, because that is a decision and not a workaround |
| **F-32** | ✅ **CLOSED** 2026-07-30. All three gaps. (1) The container suite runs without Docker (`npm run test:integration`) — 133 tests against PostgreSQL 16.6 + pgvector 0.8.1 — so entity/schema alignment no longer rests on `ddl-auto: validate` alone. (2) **Adapter round-trips:** `KnowledgePersistenceIT`, 19 tests over all ten knowledge mappers, writing rows as SQL and reading them back through `KnowledgePort`. The load-bearing one is provenance — `KnowledgeProvenanceMapper` assembles from two rows and its own javadoc says MapStruct "would still compile, having quietly picked one of the two", so the test puts the source's `retrieved_at` seven years from the row's. Also covered: the `tags text[]` array, `numeric(9,6)` coordinates, `Money` exactness, `duration_minutes` as minutes not seconds, absent optionals staying absent, every declared ordering, the POI category filter, V21's country join, and `search()`'s hand-written pgvector SQL — which had no test at all. (3) **HNSW planner usage:** measured, see `docs/KNOWLEDGE-SCHEMA.md` §5 — the index is chosen above ~1–2k rows and correctly refused below, so at ADR 010 §1's curated floor the six indexes are unused and cost write time for a corpus that does not exist yet. Gate **17B**'s "shared adapter contract tests" is satisfied by (2) |
| **F-33** | `travel_app` cannot express that one app supersedes another in a market, so task 17's "China suppression of inactive global alternatives" is not representable. Needs a schema change — a gap in task 16's design, not something a seed can work around |
| **F-34** | Real curation for Tokyo/Bangkok/Shanghai is still unstarted. SAMPLE (`stub:sample`, `PARTIAL`) stays below ADR 010 §1's floor. **Owner: task 41** (accepted debt closing task 17). Gate **17C** deferred rather than inventing FULL provenance. |
| **F-35** | `PLAN.md` §4.1.3 and `BACKLOG.md` S3-1 still specify `PUT .../brief/clarification`; ADR 008 §3 supersedes it with a typed action endpoint, which is what task 18 implemented. The plan documents were not amended |
| ~~**F-36**~~ | **Resolved (task 20).** All four routes exist and the three assumptions are now contract. History: the repo's own `PageQuery` envelope — `page` zero-based, `page_size` default **20** (the client had assumed 30 and now sends 30 explicitly, which is its own choice), rejected not clamped above 100 — returning `{page, page_size, total, conversation_id, items[]}`; page 0 is the newest page and items are `seq` ascending, both documented on the path. Body: `{client_message_id, content, conversation_id?}`, adopted unchanged. `message_start` carries `client_message_id` on the user echo — pinned by `ChatSseContractTest` and `ChatTurnServiceTest`. `chatPath()` is gone; `chat-api.ts` now uses the generated path union with the same cast `admin-api.ts` makes for a query string |
| **F-37** | `DevAdminSeeder.run()` calls `seed()` by self-invocation, so its `@Transactional` proxy is bypassed and the annotation never applies. Harmless today (one insert) but it does not do what it reads as doing. Pre-existing, from task 12 |
| **F-38** | Neither `MailConfigValidator` nor `SupportedDestinationService` has a unit test. Both are startup/permission-shaped code where a silent regression is invisible — `MailConfigValidator` is the guard that stops a prod deploy accepting unverified email addresses |
| **F-39** | ADR 007 specifies resume: "client sends `Last-Event-ID`; server replays persisted frames after that id". Task 20 accepts the header and ignores it — nothing is replayed. Safe rather than silent: a reconnect re-sends the same `client_message_id`, so the user message is not duplicated, and the previous assistant turn is already marked `INTERRUPTED`. The cost is that a reconnect regenerates the answer instead of continuing it. Relatedly, `id:` is emitted only on `message_start`/`message_end` (value = the message's `seq`), not on every frame as the ADR says: token deltas are not persisted individually, so a per-delta id would promise a position nothing can replay, and repeating one message's `seq` across its own deltas would make the client's duplicate filter drop every token after the first. Amend the ADR or implement replay |
| **F-40** | The contract publishes six chat roles (`user`, `assistant`, `system`, `tool_call`, `tool_result`, `lifecycle_event`); the frontend's `ChatRole` union has three. Nothing writes the other three yet (task 20 adds no tools), but the first `tool_call` row in history will fail zod validation and blank the whole page rather than one message. Tasks 21/22 must widen `ChatRole` and decide how a tool row renders — §7.2 forbids showing raw tool JSON |
| **F-41** | ✅ **CLOSED** 2026-07-30. `RefreshTokenService.rotate` was `@TransactionalWrite` while calling `SessionRevocationService.revokeAllSessions`, which is `REQUIRES_NEW`. That suspends the caller's transaction — keeping its pooled connection — and asks for a second, so N concurrent refreshes of one token wanted 2N connections from a pool of 10 while the suspended transactions held the row lock the revocation needed. Not a database deadlock, so nothing detected it and nothing timed out: the endpoint stopped answering. Eight threads hung for 25 minutes. The same annotation also carried `@Retryable(CannotAcquireLockException)`, which would have retried the whole rotation — and the retry reads its own committed `rotated_at`, calls it a replay, and signs the account out everywhere. `rotate` is no longer transactional; the conditional `UPDATE` owns its boundary in the adapter, as `AccountTokenRepositoryAdapter` already does for task 09's single-use tokens. **Found by running the Testcontainers suite for the first time on this machine** |
| **F-42** | ✅ **CLOSED** 2026-07-30. Five integration tests asserting the email-verification gate had been failing **since task 09**. `RegistrationService.autoVerify()` returned `mail.isStub()`, and the whole integration suite runs on the stub mailer — it is what `AccountLifecycleApiIntegrationTest` reads captured mail out of — so every account the suite registered came out already verified and task 08's assertions could not pass. Never noticed because the Testcontainers suite needs Docker, which this machine does not have, and the 2026-07-29 review explicitly could not reach CI to check. Fixed by separating "which mailer" from "is verification required": `travelplanner.mail.auto-verify-registrations` defaults to `isStub()` so local development is unchanged, and the integration profile sets it `false` |
| **F-43** | ✅ **CLOSED** 2026-07-30 / updated 2026-08-04. `FlywayMigrationIntegrationTest.createsExactlyTheTablesThisTaskOwnsAndNoneBelongingToLaterFeatures` asserted that `destination`, `poi`, `conversation` and `message` do **not** exist — a task-07-era rule that tasks 16 (V13–V18) and 20 (V19) legitimately superseded without updating the list. Failing since task 16, invisibly, for the same Docker reason. Task 23 moved `research_job` off the list; task 25 moved `ranked_recommendation` (+ `research_run_result`) off the list. Remaining “must not exist”: `booking`, `itinerary_day`, `itinerary_item` |
| **F-46** | ✅ **CLOSED** 2026-07-30 (found while building gate 17B's fusion). V15 built `ix_poi_fulltext` with the `simple` text-search configuration, which does no stemming — so `websearch_to_tsquery('simple','temples')` does not match a POI named "Sample Old Town Temple", and `markets` does not match "Market". Measured on PostgreSQL 16.6; `english` matches both. ADR 010 §5 and task 17's DoD name **street food** and **temples** as the two queries hybrid retrieval exists to serve, so the lexical arm could answer one of the two. Invisible because the sample seed's temple POI happens to carry the plural in its description as well as the singular in its name — a test written against seeded data passes either way, and only a fixture that deliberately omits the plural distinguishes the configurations. Migration **V22** rebuilds the index with `english`; pinned by `KnowledgeHybridSearchIT.hybridMatchesTemplesAgainstASingularTempleName` and its converse |
| **F-44** | ✅ **CLOSED** 2026-07-30. `price_history.amount` is one `numeric(12,2)` column for every currency, while `Money` enforces the currency's own minor units, so `4000.10 JPY` inserted cleanly and then threw on **every read** of that destination's price history. Refusing beats truncating (a price that quietly disagrees with its source is the invented fact PLAN §4.1.0 forbids), so the fix is to catch it at seed time. `SampleSeedDomainCheck` validates a file by **constructing the domain objects it becomes** rather than by restating their rules — `Money` decides what a valid amount is, `Destination` what a timezone is, `TravelAppReplacement` what a suppression key is — so every invariant a record gains in future is enforced the day it is written and no checklist can drift from the records. This mattered beyond the yen case: *every* rule living in a record constructor had the same load-green-fail-on-read shape. Cross-node facts no record can see are checked alongside (an `area_slug` naming an undefined area, duplicate slugs, non-twelve seasonality, a route segment from an area to itself), and all problems are reported in one pass. Wired into `SampleKnowledgeReader.readDestination()`, deliberately the only path that reads a destination file, because an opt-in validator would reproduce the defect it fixes. `SampleSeedValidationTest` is the CI gate 17B owed — the real reader over the real files, no database or Docker, so it runs in `./gradlew test` and `verify:fast`; `npm run seed:validate` is a fast convenience over the same check for the curation loop. Two things fell out of building it: `ValidationFailedException.getMessage()` is the user-facing "The request is not valid." and hid the one useful fact, now unwrapped from `details().fields`; and Gradle's default `testLogging` drops exception messages, so `exceptionFormat = FULL` is set project-wide. Also pinned by `KnowledgePersistenceIT.aYenPriceWithFractionalDigitsIsRefusedRatherThanTruncated` |
| **F-41** | `fetchChatHistory` re-sorts a page by `created_at` before returning it. That was defensive when no ordering was published; the contract now guarantees `seq` ascending, and V19's own header records that `created_at` is *not* an ordering (fixed per transaction in Postgres, and clocks move backwards). The sort is stable so identical timestamps keep the server's order, which is why it is harmless today — but under clock skew it would reorder a turn the server had ordered correctly. Delete it and trust the contract |

| **F-39** | ✅ **CLOSED** 2026-07-30. `Last-Event-ID` was accepted and ignored while the frontend implemented its entire half of the protocol — cursor, header, idempotent drop of replayed frames. Safe, because the `client_message_id` key stops a duplicate question, and expensive in three ways nothing reported: a second provider call billed, a **different** answer shown than the one the user had started reading, and two assistant messages in history for one question. Now honoured by `ChatTurnService.replay` at the granularity that exists — frames are not persisted, messages are — so a reconnect whose turn already reached `COMPLETE` is answered from the database with `done`/`stop_reason: replay` and no model call. A turn cut mid-sentence still regenerates: the half-sentence the client holds came from deltas nothing stored, and prefilling the model with its own partial output was rejected as a new generation dressed up as a resumption. ADR 007's wording was amended rather than implemented as written — "every frame carries a monotonic id" was never achievable, and its resume row now states the four cases. 14 backend tests (`ChatReplayTest`) + 3 frontend (`chat-state.test.ts`) |
| **F-40** | The client's `ChatRole` union has 3 members; the contract publishes 6. The first `tool_call` history row will fail zod and blank the whole page. Nothing writes those rows yet — **tasks 21/22 must widen it before adding tools** |
| **F-41** | `fetchChatHistory` still re-sorts by `created_at`. Harmless today (stable sort) but V19's own header states `created_at` is not an ordering. Now that `seq` order is contractual, delete the sort |
| **F-42** | `surprise_me` (UC-C1-05) has no persistence column and no `TripBriefDetails` field, so the flag is lost on save. Needs a migration + a record change task 18 owns |
| **F-43** | `ai_call_log.total_tokens` is always 0 for `complete()` calls: `LlmClientRouter` only extracts `Usage` from an `LlmCompletion`, but `LlmPort.complete` returns a bare `String`. Latency/provider/cost/prompt-version do log. Fixing it changes task 14's port contract |
| **F-45** | Chat history deep paging is linear — `history` reads `(page+1)*size` and keeps the leading slice, because the port publishes no offset-from-the-end read and `seq` may have gaps. The fix is a cursor-paged port method |

**Active blockers:** none. **B-3** (`gh` unauthenticated) is informational only.

## Milestone — Phase 1 started: TKB schema and domain (2026-07-29)

Task 15 merged (`95970f5`), closing Phase 0B. Task 16 is underway on `dev` — schema and domain
landed, persistence and its Testcontainers suite still to come.

| Slice | Commit | Evidence |
|---|---|---|
| Migrations V13–V18 | `c68ff6f` | 12 tables and 6 partial HNSW indexes created on Postgres 16.6 + pgvector 0.8.1 |
| Domain, ports, typed refusal | `bbdc065` | 212 tests; LINE 88.87% / BRANCH 81.73% against gates of 85 / 70 |

Three ADR 010 rules are now structural rather than conventional, each because it fails **silently**
when it fails at all:

| Rule | Enforced by | What silence would look like |
|---|---|---|
| One embedding model per index (§5) | `vector(1536)` type + model CHECK + partial index on the model name | Two models' vectors in one index; retrieval degrades with no error |
| Destination filtered pre-ANN (§5) | One partial HNSW index per destination; `KnowledgeQuery.destinationId` non-null | Post-filtering discards the global top-k; a small destination returns nothing |
| Coverage is a typed outcome (§4) | `Destination.requireRankable` → `destination_not_covered`, registered end to end | An uncovered city ranks last and reads as "considered and rejected" |

Adding a fourth destination needs a migration, because a partial-index predicate must be a
constant. ADR 010 already anticipates this: *"Adding a destination is a documented, repeatable
authoring procedure."*

**Remaining for task 16:** JPA entities and MapStruct adapters, the Testcontainers migration /
constraint / adapter suite, and the handoff doc naming the seed format for task 17 and the columns
tasks 40 and 41 write. Task 20 is independently unblocked and needs nothing from task 16.

Four inconsistencies found in this code were left unfixed rather than silently patched — **F-27**
to **F-30**. F-29 is the one with a downstream dependency: `Destination.timezone` is never
validated as an IANA zone, and task 28 needs it to place an itinerary event on a clock.
