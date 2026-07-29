# Handoff — remaining work and how to execute it

> For whoever (or whatever) picks this up next. Written 2026-07-29, after tasks 16–20.
>
> This document does **not** replace [`AGENTS.md`](../AGENTS.md),
> [`docs/AGENT-HARNESS.md`](AGENT-HARNESS.md), [`docs/AI-AGENT-WORKFLOW.md`](AI-AGENT-WORKFLOW.md)
> or [`tasks/README.md`](../tasks/README.md) — read those first, in that order. This adds what
> those cannot: the execution order for what is left, the gates as they actually behave today, and
> the specific traps that cost real time in this repository.

---

## 1. Where the project is

`dev` is the working branch. **Next free migration: `V21`.**

| Phase | Tasks | State |
|---|---|---|
| 0A/0B — foundation + platform | 00–15 | `done` |
| Phase 1 — knowledge, intake, chat | 16 `done` · 17, 18, 19, 20 `in_progress` | see §2.0 |
| Phase 1 — research, itinerary | 21–31 | not started |
| Phase 2 — booking, runtime | 32–37 | not started |
| Knowledge ops | 40, 41 | not started |
| Integration | 38, 39 | not started |

[`tasks/STATUS.md`](../tasks/STATUS.md) is the single source of truth for status and carries the
open-question register (`F-nn`). Update it in the same commit as the work, never afterwards.

---

## 2. Execution order

### 2.0 Finish 17–20 first — none of them is `done`

All four have substantial work landed and a named remainder. **Close these before starting 21**,
because 21 depends on all of 18, 19 and 20, and building on an unfinished dependency is how the
rework starts.

| Task | What is left | Size |
|---|---|---|
| **17** | Seed validation command for CI, hybrid vector + `tsvector` fusion, shared adapter contract tests | medium |
| **18** | Frontend brief editor + `locales/{en,ms}/trip_brief.json` (7 clarification keys) | medium |
| **19** | `surprise_me` persistence — needs a migration **and** a `TripBriefDetails` field (F-42) | small |
| **20** | `Last-Event-ID` resume, or amend ADR 007 to drop the per-frame id promise (F-39) | small–medium |

Two of these are cheap and unblock disproportionately: **F-40** (widen the client `ChatRole` union
to the 6 the contract publishes — the first `tool_call` history row will otherwise blank the whole
page, and tasks 21/22 are what write those rows) and **F-42**.



The dependency graph collapses to one long critical path with a few genuine forks. Do **not**
invent parallelism the graph does not offer — every task below lists what it truly needs.

### Immediately available once 17–20 are `done`

| Task | Needs | Notes |
|---|---|---|
| **21** Planner chat and trip creation | 18, 19, 20 | **The first end-to-end product loop closes here.** Highest value in the backlog. |
| **40** TKB refresh and re-embedding | 14, 15, 16, 17 | Independent of the whole chat/research chain — safe to run in parallel with 21. |

That is the one clean fork available now: **21 and 40 together.**

### The critical path after 21

```
21 → 22 → 23 → 24 → 25 → 26 → 27 ┐
                      26 → 28 → 29 → 30 → 31 ┤
                                             ├→ 35 → 36 → 37 → 38 → 39
                             32 → 33 → 34 ───┘
```

Practical reading:

1. **21 → 22** — chat tools. Strictly sequential.
2. **23 → 24 → 25 → 26** — research. 24 needs 23; 25 needs 24; 26 needs all three.
3. **28 → 29 → 30 → 31** — itinerary. Forks from 26, so it can run alongside 27.
4. **32 → 33 → 34** — booking. Forks from 31; 34 can trail while 35 proceeds.
5. **41** needs 40 — pair them.
6. **38** needs everything; **39** needs 38.

### Genuine parallel pairs

| Run together | Why it is safe |
|---|---|
| 21 + 40 | Disjoint: chat versus knowledge refresh |
| 27 + 28 | Both fork from 26 and touch different domains |
| 34 + 35 | 34 is adapters, 35 is chat tools |
| 40 + 41 | Sequential in the graph but the same subsystem — one agent can do both |

**Everything else is sequential.** Running two agents over one task's dependencies produces work
that must be thrown away.

---

## 3. The gates — what fails a build, and how to satisfy it honestly

`./gradlew build` and the frontend chain are the contract. Full detail:
[`docs/QUALITY-GATES.md`](QUALITY-GATES.md).

```bash
cd apps/backend && ./gradlew build
```

```bash
cd apps/frontend && npm run lint && npm run format:check && npm run typecheck && npm run test:coverage && npm run build
```

| Gate | Threshold | Fails when |
|---|---|---|
| Checkstyle | 120 cols, naming, imports | any violation, all three source sets |
| ArchUnit | 8 rules | a layer boundary is crossed |
| JaCoCo | LINE ≥ 85%, BRANCH ≥ 70% over `domain/**` + `application/**` | new logic without tests |
| Vitest | statements/lines ≥ 35, functions ≥ 55, branches ≥ 70 | same, frontend |
| OpenAPI drift | regenerate + `git status --porcelain` | `src/generated/` is stale |
| ESLint zones | app/features/shared/generated | a cross-layer import |

### The rule that matters most

> **Never lower a threshold, add an exclusion, or write an assertion-free test to make a gate pass.**

Task 15 states this and it is not decoration. The JaCoCo ratchet has already caught two agents
shipping half-finished work in this repository. It only keeps working if nobody files down the
teeth. Coverage today sits at **LINE ~92% / BRANCH ~87%** — well above the gate, because every
task so far raised it. Keep it that way.

If a gate is genuinely wrong, the process is in `QUALITY-GATES.md` §5: state the violation, argue
the boundary rather than the change, record an `F-nn`, and move the rule in the same commit as the
code with the rationale in the rule's own comment.

---

## 4. Traps — each of these cost real time here

These are ordered by how silently they fail. The top ones produce **no error at all**.

### 4.1 A bound parameter disables a partial index

`V18` creates one partial HNSW index per destination so the filter runs before the ANN search.
Postgres only uses a partial index when it can prove the query predicate implies the index
predicate — and `destination_slug = $1` does **not** imply `destination_slug = 'tokyo-jp'` under a
generic plan. Binding it silently disables the index; recall degrades and nothing fails.
`KnowledgeVectorSearch` therefore interpolates a strictly-validated slug literal. Do not "clean
this up" into a bind parameter.

### 4.2 `now()` is fixed for a whole transaction

Every row written in one transaction shares `created_at` byte-for-byte, so ordering by timestamp is
**undefined**, not merely imprecise. `message.seq` is allocated from the parent under
`SELECT … FOR UPDATE`. `MAX(seq)+1` is not a substitute — it is a read two appenders can both
perform and both believe.

### 4.3 ESLint `no-restricted-imports` does not merge

Flat-config objects replace rule *options* rather than merging them. An earlier config expressed
four boundaries as cascading blocks, the last matching `src/**` — three of the four rules silently
did nothing while `npm run lint` passed. Zones are now **disjoint** and each restates every
restriction. After changing them, prove the gate still bites using the probe procedure in
`QUALITY-GATES.md` §6. **A boundary rule that cannot be shown to fail is not a gate.**

### 4.4 `@Transactional` bypassed by self-invocation

`DevAdminSeeder.run()` calls `seed()` directly, so the proxy is skipped and the annotation never
applies (recorded as **F-37**). If a class calls its own `@Transactional` method, the annotation is
decorative. Put the transactional work on a separate bean.

### 4.5 A bare `@Component` breaks the unit suite

`./gradlew test` must run with no Docker and no database, so the test profile excludes
`DataSourceAutoConfiguration`. Any bean touching a datasource needs `@RequiresDatabase`
(`config/RequiresDatabase.java`) or `contextLoadsAndReadinessIsWired` fails with a confusing
`EntityManagerFactory` error.

### 4.6 MapStruct treats single-arg domain methods as setters

`unmappedTargetPolicy = ERROR` flags `Message.complete(…)`, `Conversation.archive(…)` and similar
as unmapped targets. Add per-property `@Mapping(ignore = true)` with a reason — **do not** relax the
policy method-wide, which would also hide a genuinely forgotten column.

### 4.7 Provenance timestamps

`KnowledgeProvenance.retrievedAt` comes from the **row**, not from the joined source. The row's
timestamp is that fact's freshness and is what the TTLs measure against. Getting it backwards makes
staleness wrong everywhere while still returning a plausible citation.

### 4.8 Environment quirks on this machine

- Docker is **not available**; there is native PostgreSQL 16.6 + pgvector 0.8.1 on `localhost:5432`.
  Testcontainers suites cannot run. Verify schema/entity alignment by booting with
  `ddl-auto: validate` instead — it checks every mapping and is real evidence.
- Backend runs on **8081** (`SERVER_PORT` in `.env`) because Oracle XE owns 8080.
- The database is `postgres` with the `postgres` superuser — a deviation from the plan's
  `travel_planner`, deliberately chosen by the owner.
- Git may report `LF will be replaced by CRLF`; harmless, but a test asserting a literal `\n`
  inside a migration will fail on a `git archive` export.

---

## 5. Conventions to match

Read one existing example before writing anything new; the codebase is highly consistent.

| Layer | Reference file |
|---|---|
| Domain record | `domain/model/Trip.java`, `domain/model/Destination.java` |
| Domain exception | `domain/exception/UserNotFoundException.java` |
| Migration | `db/migration/V5__create_trip_table.sql` |
| JPA entity | `infrastructure/persistence/entity/TripEntity.java` |
| Mapper | `infrastructure/persistence/mapper/TripPersistenceMapper.java` |
| Adapter | `infrastructure/persistence/TripRepositoryAdapter.java` |
| Controller | `api/controller/AdminUserController.java` |
| Startup validator | `config/AiConfigValidator.java`, `config/MailConfigValidator.java` |
| Frontend feature | `apps/frontend/src/features/auth/` |

Non-negotiables:

- **Records, immutable, validating compact constructors.** Defensive copies for arrays/collections
  in *and* out — a record's generated accessor hands back internal state otherwise.
- **Domain is framework-free.** Only `java.*` and `com.travelplanner.domain.*` (plus `reactor..`,
  which is a knowing exception — see F-23).
- **Entities never leave `infrastructure.persistence`.** Every port returns a domain type.
- **Every new error code goes in BOTH `errors.yaml` and `ApiErrorCode`**, or it is silently
  downgraded to `internal_error`.
- **Javadoc explains *why*, not *what*.** Match the surrounding density. The comments here carry
  design rationale that would otherwise be lost — that is why they are long.
- **snake_case on the wire**, handled globally by Jackson. Do not add `@JsonProperty` for it.
- **Money is `BigDecimal` + currency, crossing the wire as a decimal string** so a JS `double` does
  not undo the precision at the last hop.
- **No `PATCH`.** Typed action endpoints (`POST .../actions/<verb>`) instead.
- **ADR 008**: every write carries `expected_version`; mismatch is `409 version_conflict` with
  `details.current_version`. Bind it as `Integer` + `@NotNull` — a primitive binds absence to `0`
  and turns a forgotten field into a force-overwrite.

---

## 6. Findings that must be closed, and by whom

`F-nn` entries live in `tasks/STATUS.md`. These are the ones with a downstream consumer — closing
them late costs more than closing them on time.

| Finding | What | Close it in |
|---|---|---|
| **F-34** | Real curation unstarted; sample data is `PARTIAL`, so `findSupportedDestinations()` is **empty** and no destination can be ranked | Before **24** (ranking); ideally now |
| **F-29** | `Destination.timezone` never validated as an IANA zone | **28** — it places itinerary events on a clock |
| **F-33** | `travel_app` cannot express one app superseding another in a market | **40/41**, needs a migration |
| **F-32** | Adapter round-trips, concurrent constraint behaviour and actual HNSW index selection unverified | When data exists — check `EXPLAIN ANALYZE` names `ix_poi_embedding_hnsw_<destination>` |
| **F-22** | LLM stub not blocked in production (knowledge stub is) | **39** at the latest |
| **F-23** | Reactor in `domain/` — delete the allow-list entry if resolved | **36/37** |
| **F-27** | `DestinationArea` coordinates unchecked — record and V14 must move together | Any migration touching V14 |
| **F-31** | Chat enum casing disagreement; client normalises defensively | **20**, then delete the client-side normalisation |
| **F-36** | Frontend assumed three chat contract details | **20** — publish them |
| **F-38** | `MailConfigValidator` and `SupportedDestinationService` untested | Any task touching them |

Two more that are cheap and worth doing opportunistically: **F-37** (inert `@Transactional`) and
`ai_call_log` recording zero tokens for `complete()` calls because `LlmPort.complete` returns a
bare `String` (task 14's contract).

---

## 7. Definition of done

A task is `done` only when it is **merged** *and* the `AGENT-HARNESS.md` §6 evidence gate passed
**with real command output**. Generated code alone never justifies `done`.

Evidence means pasting what actually ran — `BUILD SUCCESSFUL`, the test count, the coverage
figures, the endpoint response. Not "tests pass".

Where a task's DoD cannot be met (as with Testcontainers here), say so explicitly, record what was
verified instead and what remains unverified, and register an `F-nn`. Do not quietly redefine the
DoD to match what was achievable.

---

## 8. If you use parallel agents

It works, and it found several real bugs that a single pass would have missed — including one in
code I had just written. Two rules learned the hard way:

**Give agents disjoint file sets, and treat hand-authored contract files as exclusive.**
`openapi.yaml`, `errors.yaml`, `MigrationContractTest` and `SecurityConfig` are shared resources.
Tasks 17, 18 and 20 ran in parallel over `openapi.yaml` and produced work that **could not be split
into separate commits** — commit `50c2d68` covers three tasks because any split would leave a
commit whose contract did not match its controllers. Assign such a file to exactly one agent at a
time.

**Allocate migration numbers up front.** Two agents both reaching for `V19` is a silent collision.
Hand each one its number in the prompt.

Expect the shared tree to be red while agents are in flight; that is normal, not a failure. Verify
only once they have all landed, and verify yourself rather than trusting the report — the reports
here were accurate, but the whole point of a gate is that it does not rely on trust.
