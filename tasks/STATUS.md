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
| 16 | [Knowledge domain and schema](16-knowledge-domain-schema.md) | 07, 14, 15 | `done` | Commits `c68ff6f` (schema), `bbdc065` (domain). Migrations **V13–V18** applied against real Postgres 16.6 + pgvector 0.8.1: 12 tables, 6 partial HNSW indexes, and each guard proven to reject a bad write. Pure domain + `KnowledgePort` + typed `destination_not_covered` (registered end to end). 212 domain tests; coverage LINE 88.87% / BRANCH 81.73%. Persistence `8db9029`: 10 entities, 10 repositories, 10 mappers, adapter, native pgvector search. Handoff: [`docs/KNOWLEDGE-SCHEMA.md`](../docs/KNOWLEDGE-SCHEMA.md). **Testcontainers waived by the owner**; entity/schema alignment proven instead by `ddl-auto: validate` booting against Postgres 16.6 — see F-32. **Next free migration: `V21`.** New: **F-27**–**F-30**, **F-33**. |
| 17 | [Knowledge seed and retrieval](17-knowledge-seed-retrieval.md) | 14, 16 | `in_progress` | Commit `50c2d68`. Seed format + idempotent loader + SAMPLE dataset (3 destinations, 142 rows, 39 embedding chunks) seeded **PARTIAL**; reader refuses a file declaring `FULL`. `GET /destinations/supported` public per ADR 010 §4. Prod refuses any `Stub*` knowledge bean. **Remaining: seed validation command for CI, hybrid vector+tsvector fusion, shared adapter contract tests.** Real curation is an open authoring task — see **F-34**. New: **F-33**. |
| 18 | [Trip and TripBrief core](18-trip-brief-core.md) | 06, 07, 11, 15, 17 | `in_progress` | Commit `50c2d68`. Migration **V20** adds the brief columns V6 deferred. Trip + brief CRUD, derived clarification, `DRAFT`/`CLARIFICATION_NEEDED`/`BRIEF_COMPLETE` only, ADR 008 `expected_version` on every write with `409 version_conflict` + `details.current_version`. **Started while 17 was `in_progress`** — the interface it needed (`findSupportedDestinations`, `destination_not_covered`) already existed from 16. **Remaining: frontend brief editor + `locales/*/trip_brief.json`.** New: **F-35**. |
| 19 | [LLM TripBrief extraction](19-llm-trip-brief-extraction.md) | 14, 18 | `in_progress` | Commit `1262401`. Versioned prompt `trip-brief-extract@v1` gated on the SHA of the **rendered** block. Structural prompt-injection separation (template variables cannot accept user text; fenced USER message; assertion that no user text reaches the system block). Domain validation authoritative — refused values are dropped so `forDetails()` asks about them. Retry hard-capped at one repair, then deterministic fallback. 11 golden fixtures incl. injection, multilingual `ms`, timeout, malformed. **Started while 18 was `in_progress`** — 18's backend, which it extracts into, was complete. **Remaining: `surprise_me` persistence (needs a migration + a `TripBriefDetails` field, task 18 owns that record).** |
| 20 | [Conversation persistence and SSE](20-conversation-sse.md) | 06, 07, 11, 14, 15 | `in_progress` | Commit `50c2d68` (persistence + client) plus the SSE slice, uncommitted. Migration **V19** (`planner_session`, `conversation`, `message`). Per-conversation `seq` under `SELECT … FOR UPDATE`; `client_message_id` unique index for idempotency; partial messages marked, never discarded; no chain-of-thought storable. **Both routes now stream**: `POST`/`GET` on `/planner/chat/messages` and `/trips/{tripId}/chat/messages`, published in the contract with an `x-sse-stream` marker and a description-only body (ADR 007). A turn is three short write transactions with the model call strictly between them — nothing `@Transactional` exists in `application/chat/ChatTurnService`. Disconnect marks the partial answer `INTERRUPTED`; the provider-event translation is an allow-list with no default branch. Backend coverage LINE 92.3% / BRANCH 86.8% (`application/chat` 95.0% / 83.6%); frontend 331 tests green. **F-31** and **F-36** closed. **Remaining: `Last-Event-ID` frame replay (accepted and ignored today — see F-39), and the client role union must gain the three tool roles when tasks 21/22 write them (F-40).** |
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
| `done` | **17** |
| `in_progress` | 4 |
| `blocked` | 0 |
| `review` | 0 |
| `not_started` | 21 |

*42 tasks total — 40 original plus 40/41 added by ADR 010.*

## Milestone — Phase 0B platform complete (2026-07-28)

Tasks 00–14 are `done`. Tagged `v0.2.0-phase-0b`.

Everything a feature needs now exists: contract, database, identity, mail,
admin, frontend platform, PWA, and the AI runtime. Task 15 (quality gates) closed Phase 0B and
merged as `95970f5`, which unblocked tasks 16 and 20.

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
| **F-27** | `DestinationArea` enforces the lat/long pairing rule but does **not** range-check coordinates, unlike `Destination` and `Poi`. Matches V14, which also has only the pairing constraint. Areas are what C3 schedules against geographically, so a nonsense coordinate is least likely to be noticed here — fix the record and V14 together |
| **F-28** | `KnowledgeQuery.equals`/`hashCode` are identity-based on the `float[] embedding` component, so two queries built from identical inputs are never equal. Normal for records with array components, but this type is documented as a value object and takes care to be immutable, so the gap is surprising. Matters if a query is ever used as a cache key (task 37) |
| **F-29** | `Destination` validates neither `name` nor `timezone` for blankness, while `DestinationArea`, `Poi`, `TransportMode` and `TravelApp` all reject a blank name. `timezone` is never checked as a valid IANA zone despite the javadoc requiring one before an itinerary can place an event on a clock (task 28 depends on this) |
| **F-30** | `DestinationNotCoveredException.supportedSlugs` is `transient`, so it deserialises to `null` although `supportedSlugs()` documents no null contract. Mirrors `DomainException.details`, so it may be deliberate — decide and document, or drop `transient` |

| ~~**F-31**~~ | **Resolved (task 20).** Settled in the DTO layer: `api/dto/chat/ChatWireNames` is the one place a chat enum becomes a wire value, Java stays `UPPER_SNAKE` (they are the persisted values and two CHECK constraints), and the wire is lower-case snake. Scoped to chat only — `roles` and `linked_providers` stay upper-case. `ChatWireNamesTest` asserts it for every constant of both enums, so a constant added later is published correctly by default. Both client-side `toLowerCase()` normalisations are deleted; the four-statuses-onto-two collapse stays, because that is a decision and not a workaround |
| **F-32** | Task 16's Testcontainers suite was waived. Entity/schema alignment IS proven (`ddl-auto: validate` boots against Postgres 16.6 + pgvector 0.8.1), but three things remain unverified: adapter round-trip mapping, constraint behaviour under concurrent writes, and whether the partial HNSW indexes are actually chosen by the planner — on empty tables `EXPLAIN` reports a seq scan regardless. Re-check once task 17 seeds data |
| **F-33** | `travel_app` cannot express that one app supersedes another in a market, so task 17's "China suppression of inactive global alternatives" is not representable. Needs a schema change — a gap in task 16's design, not something a seed can work around |
| **F-34** | Real curation for Tokyo/Bangkok/Shanghai is still unstarted. The committed dataset is deliberately SAMPLE (`stub:sample`, `PARTIAL`, obviously-fake names/hours/prices) and is below ADR 010 §1's floor of 25 POIs. ADR 010 §1: "curation is a deliberate authoring task with a named owner" — that owner has not been named |
| **F-35** | `PLAN.md` §4.1.3 and `BACKLOG.md` S3-1 still specify `PUT .../brief/clarification`; ADR 008 §3 supersedes it with a typed action endpoint, which is what task 18 implemented. The plan documents were not amended |
| ~~**F-36**~~ | **Resolved (task 20).** All four routes exist and the three assumptions are now contract. History: the repo's own `PageQuery` envelope — `page` zero-based, `page_size` default **20** (the client had assumed 30 and now sends 30 explicitly, which is its own choice), rejected not clamped above 100 — returning `{page, page_size, total, conversation_id, items[]}`; page 0 is the newest page and items are `seq` ascending, both documented on the path. Body: `{client_message_id, content, conversation_id?}`, adopted unchanged. `message_start` carries `client_message_id` on the user echo — pinned by `ChatSseContractTest` and `ChatTurnServiceTest`. `chatPath()` is gone; `chat-api.ts` now uses the generated path union with the same cast `admin-api.ts` makes for a query string |
| **F-37** | `DevAdminSeeder.run()` calls `seed()` by self-invocation, so its `@Transactional` proxy is bypassed and the annotation never applies. Harmless today (one insert) but it does not do what it reads as doing. Pre-existing, from task 12 |
| **F-38** | Neither `MailConfigValidator` nor `SupportedDestinationService` has a unit test. Both are startup/permission-shaped code where a silent regression is invisible — `MailConfigValidator` is the guard that stops a prod deploy accepting unverified email addresses |
| **F-39** | ADR 007 specifies resume: "client sends `Last-Event-ID`; server replays persisted frames after that id". Task 20 accepts the header and ignores it — nothing is replayed. Safe rather than silent: a reconnect re-sends the same `client_message_id`, so the user message is not duplicated, and the previous assistant turn is already marked `INTERRUPTED`. The cost is that a reconnect regenerates the answer instead of continuing it. Relatedly, `id:` is emitted only on `message_start`/`message_end` (value = the message's `seq`), not on every frame as the ADR says: token deltas are not persisted individually, so a per-delta id would promise a position nothing can replay, and repeating one message's `seq` across its own deltas would make the client's duplicate filter drop every token after the first. Amend the ADR or implement replay |
| **F-40** | The contract publishes six chat roles (`user`, `assistant`, `system`, `tool_call`, `tool_result`, `lifecycle_event`); the frontend's `ChatRole` union has three. Nothing writes the other three yet (task 20 adds no tools), but the first `tool_call` row in history will fail zod validation and blank the whole page rather than one message. Tasks 21/22 must widen `ChatRole` and decide how a tool row renders — §7.2 forbids showing raw tool JSON |
| **F-41** | `fetchChatHistory` re-sorts a page by `created_at` before returning it. That was defensive when no ordering was published; the contract now guarantees `seq` ascending, and V19's own header records that `created_at` is *not* an ordering (fixed per transaction in Postgres, and clocks move backwards). The sort is stable so identical timestamps keep the server's order, which is why it is harmless today — but under clock skew it would reorder a turn the server had ordered correctly. Delete it and trust the contract |

| **F-39** | `Last-Event-ID` is accepted and ignored — a reconnect regenerates the turn rather than resuming it (safe: same `client_message_id`, prior turn already `INTERRUPTED`). Task 20 also deliberately deviates from ADR 007's "every frame carries a monotonic id": `id:` is emitted only on `message_start`/`message_end`. A per-delta id would promise a replay position nothing persists, and repeating one `seq` across a message's deltas would make the client's `current <= last` filter drop every token after the first. **Amend ADR 007 or build replay** |
| **F-40** | The client's `ChatRole` union has 3 members; the contract publishes 6. The first `tool_call` history row will fail zod and blank the whole page. Nothing writes those rows yet — **tasks 21/22 must widen it before adding tools** |
| **F-41** | `fetchChatHistory` still re-sorts by `created_at`. Harmless today (stable sort) but V19's own header states `created_at` is not an ordering. Now that `seq` order is contractual, delete the sort |
| **F-42** | `surprise_me` (UC-C1-05) has no persistence column and no `TripBriefDetails` field, so the flag is lost on save. Needs a migration + a record change task 18 owns |
| **F-43** | `ai_call_log.total_tokens` is always 0 for `complete()` calls: `LlmClientRouter` only extracts `Usage` from an `LlmCompletion`, but `LlmPort.complete` returns a bare `String`. Latency/provider/cost/prompt-version do log. Fixing it changes task 14's port contract |
| **F-44** | Chat history deep paging is linear — `history` reads `(page+1)*size` and keeps the leading slice, because the port publishes no offset-from-the-end read and `seq` may have gaps. The fix is a cursor-paged port method |

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
