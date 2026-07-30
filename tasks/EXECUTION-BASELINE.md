# Execution Baseline — Travel Planner Implementation

> Deliverable of [Task 00](00-plan-baseline.md). This is the execution-control layer for all
> implementation work — dependency graph, authority order, blockers, handoff and evidence rules.
>
> **This document is a snapshot of 2026-07-25, and §1 is history rather than current state.** The
> repository was empty of application code on that date; it is not now. Phase 0 is complete and
> Phase 1 is under way — `apps/backend` and `apps/frontend` are both real, both build, and both
> gate in CI. For what exists today, read [`STATUS.md`](STATUS.md); for what to do next, read
> [`../docs/HANDOFF-REMAINING-WORK.md`](../docs/HANDOFF-REMAINING-WORK.md).
>
> §§2–9 — the graph, the authority order, the evidence rules — are still in force.
>
> Companion document: the task status ledger in [`STATUS.md`](STATUS.md).

**Established:** 2026-07-25
**Baseline commit:** `aa20043` — *docs: enhance agent instructions and introduce agent harness*
**Baseline branch:** `master`

---

## 1. Repository state at baseline

| Check | Result |
|---|---|
| `master` HEAD | `aa20043` |
| `origin/master` | `aa20043` — in sync, nothing unpushed |
| `dev` | `aa20043` — identical to `master` (0 ahead / 0 behind) |
| `main` | `b8cfec6` — **38 commits behind `master`**, contributes no unique file content |
| Application scaffold present | **Not at baseline** — created by tasks 01–05; see below |
| Working tree | Clean |

### Supersedes the commit recorded in `tasks/README.md`

`tasks/README.md` records base commit `50f5d01ae3751bd84e906c530d99175e9f84fda5`. That commit is a
valid ancestor of `master` but is no longer the tip: `aa20043` has since added `tasks/` (the 40 task
briefs) and `docs/AGENT-HARNESS.md`. **`aa20043` is the authoritative baseline for all Phase 0A
work.** `tasks/README.md` is left unedited — amending it is outside this task's scope and is
recorded as follow-up **F-1**.

### The tree was empty at baseline — HISTORY, 2026-07-25

```
$ ls package.json docker-compose.yml apps .nvmrc scripts
ls: cannot access 'package.json': No such file or directory
ls: cannot access 'docker-compose.yml': No such file or directory
ls: cannot access 'apps': No such file or directory
ls: cannot access '.nvmrc': No such file or directory
ls: cannot access 'scripts': No such file or directory
```

That was the state Phase 0A started from. **Every one of those paths exists today** — tasks 01–05
created them and tasks 06–16 filled them in. Recorded here because the dependency graph in §4 was
drawn against a genuinely empty tree, and reading the graph without that context makes its ordering
look arbitrary.

---

## 2. Remote branch and PR review

No remote branch carries implementation work that supersedes a task brief.

| Branch | Commits ahead of `master` | Disposition |
|---|---|---|
| `origin/agent/implementation-task-plan` | 0 | Fully merged |
| `origin/cursor/architecture-diagrams-1514` | 0 | Fully merged |
| `origin/cursor/chat-first-planning-5b6b` | 0 | Fully merged |
| `origin/cursor/graphql-hybrid-api-1514` | 0 | Merged then reverted — REST+OpenAPI stays locked |
| `origin/cursor/nextjs-pwa-plan-0d5b` | 0 | Fully merged |
| `origin/cursor/plan-compatibility-review-edef` | 0 | Fully merged |
| `origin/cursor/plan-tech-lead-fixes-5b6b` | 0 | Fully merged |
| `origin/cursor/separate-app-folders-5b6b` | 0 | Fully merged |
| `origin/cursor/setup-dev-environment-5550` | 0 | Fully merged |
| `origin/cursor/single-env-config-5b6b` | 0 | Fully merged |
| `origin/cursor/ui-design-theme-9aec` | 0 | Fully merged |
| `origin/dev` | 0 | Mirror of `master` |
| `origin/main` | 2 | Abandoned — stale snapshot, see **B-2** decision |

> **Coverage gap:** the open-PR review required by Task 00's Scope could not be performed through
> the GitHub API — `gh` is not authenticated in this environment (`gh auth login` required). It was
> substituted with the branch-level comparison above, which covers every remote ref. If an open PR
> exists from a ref not listed here, this table is incomplete. Recorded as blocker **B-3**.

---

## 3. Authority order (confirmed)

Reconfirmed against `tasks/README.md`, `docs/AGENT-HARNESS.md` §3, and
`docs/PLAN-COMPATIBILITY.md`. All three agree; no conflict.

| # | Source | Governs |
|---|---|---|
| 1 | [`plans/superpower/PLAN.md`](../plans/superpower/PLAN.md) | Architecture, data flow, product behavior |
| 2 | [`docs/UI-UX-DESIGN-SYSTEM.md`](../docs/UI-UX-DESIGN-SYSTEM.md) | Visual tokens, responsive behavior, accessibility |
| 3 | [`docs/adr/`](../docs/adr/) | Accepted one-way decisions (001–010) |
| 4 | [`plans/USE-CASES.md`](../plans/USE-CASES.md) | Acceptance criteria |
| 5 | [`plans/BACKLOG.md`](../plans/BACKLOG.md) | Delivery sequencing |
| 6 | `tasks/NN-*.md` | Execution-sized decomposition — may clarify, must not silently override 1–5 |

**Conflict protocol** (`docs/AGENT-HARNESS.md` §3): when sources disagree or a required decision is
unspecified — **stop, record the conflict as a blocker, and ask.** Resolution happens through a
documentation or ADR change *before* coding resumes. An agent's guess becomes a locked decision
nobody reviewed.

Documentation hazards already catalogued in `docs/AGENT-HARNESS.md` §7 are **not** to be fixed
inline; they are known-and-handled, not open blockers.

---

## 4. Dependency graph

Parsed from the `## Dependencies` section of all 40 briefs. `X → Y` means *Y depends on X*.

**Verified acyclic:** 40 nodes, 162 edges, complete topological order exists.

```
00 → 01 → 02 ┐
          03 ┴→ 04 → 05 → 06 → 07 → 08 → 09 →┬→ 10 → 11 →┬→ 12 ┐
                                             └→ 14 ──────┘  13 ┴→ 15 →┬→ 16 → 17 → 18 → 19 → 21 → 22 → 23 → 24 → 25 → 26 →┬→ 27 ┐
                                                                      └→ 20 ────────────────┘                             └→ 28 ┴→ 29 → 30 → 31 → 32 → 33 →┬→ 34
                                                                                                                                                           └→ 35 → 36 → 37 → 38 → 39
```

Exact predecessor sets are recorded per row in [`STATUS.md`](STATUS.md).

### Parallel execution waves

A wave may start only when every task in all earlier waves is `done`. Tasks **within** a wave have
no dependency on each other and may run concurrently on separate branches.

| Wave | Tasks | Notes |
|---|---|---|
| 0 | `00` | This task |
| 1 | `01` | Root tooling — gates everything |
| 2 | `02`, `03` | Backend ∥ frontend scaffold — **the only Phase 0A parallelism** |
| 3–8 | `04` → `05` → `06` → `07` → `08` → `09` | Strictly serial |
| 9 | `10`, `14` | External identity providers ∥ AI provider platform |
| 10 | `11` | Frontend platform and auth UI |
| 11 | `12`, `13` | Admin platform ∥ PWA foundation |
| 12 | `15` | Quality gates — coverage/arch thresholds land here, not before |
| 13 | `16`, `20` | Knowledge schema ∥ conversation persistence and SSE |
| 14–22 | `17` → `18` → `19` → `21` → `22` → `23` → `24` → `25` → `26` | Strictly serial |
| 23 | `27`, `28` | Research chat evaluation ∥ itinerary domain |
| 24–28 | `29` → `30` → `31` → `32` → `33` | Strictly serial |
| 29 | `34`, `35` | Payment/live adapters ∥ booking chat tools |
| 30–33 | `36` → `37` → `38` → `39` | Strictly serial |

**Read this as a ceiling, not a plan.** 34 waves for 40 tasks means the graph is almost entirely
serial. Only 6 waves offer any parallelism, and at most 2 tasks at a time. Execution is
effectively one task at a time.

> **Sequencing observation (not a blocker).** The first end-to-end *product* loop —
> chat → `create_trip` → persist → render — does not close until Task 21, after the full platform
> breadth of 06–17. Tasks 00–05 do deliver a runnable infrastructure skeleton (health endpoint +
> `docker compose up`) early, which partially covers this. Flagged because "runnable at every phase
> exit" is a stated goal; changing the order would require a `plans/BACKLOG.md` revision and is
> **out of scope here**.

---

## 5. Required handoff report

Every implementation PR reports the 8 items from `tasks/README.md` §"Standard task completion
report":

1. Task ID and source commit
2. Files and layers changed
3. Architecture decisions or deviations
4. Tests and commands run, **including exact results**
5. Stubbed versus live integrations
6. Known limitations
7. Database migration and compatibility notes
8. Handoff state for dependent tasks

## 6. Required validation evidence

`docs/AGENT-HARNESS.md` §6 applies to **every task regardless of whether its brief has a
`Validation` section** — 23 of the 40 briefs do not (`10, 12, 15, 16, 18, 20, 21, 22, 24, 25, 26,
27, 28, 29, 30, 31, 32, 33, 35, 36, 37, 38, 39`).

- [ ] `Definition of Done` quoted item by item, with evidence for each
- [ ] Commands actually run, with **real output pasted** — never "tests should pass"
- [ ] Backend touched → `./gradlew test` (plus lint/arch/coverage once Task 15 lands)
- [ ] Frontend touched → build + typecheck + tests on Node 22
- [ ] Contract touched → codegen re-run; no drift
- [ ] LLM output touched → schema or golden-file test
- [ ] `docs/AGENT-HARNESS.md` §5 scope contract complete and honest about what was skipped

**A task is never complete on generated code alone.** A truthful partial report is correct; an
optimistic one corrupts every downstream task.

---

## 7. Active blockers

| ID | Severity | Blocks | Issue |
|---|---|---|---|
| **B-3** | Low | Task 00 completeness | `gh` unauthenticated — open PRs not reviewed via API |

### B-4 — the ADRs have outrun the task briefs — ✅ RESOLVED 2026-07-26

Reconciled across eight commits. Every item in the table below is now written into the owning
brief, with the ADR listed in its `Required reading` and marked as the higher authority.

| Commit | Change |
|---|---|
| `cdbb698` | Task 16 — coverage model, licence register, embedding lifecycle, HNSW pre-ANN filtering |
| `020bd4a` | Task 17 — knowledge stub is the documented exception to §4.0.7; 3 curated destinations; hybrid retrieval; freshness TTLs |
| `3d8bac3` | **Task 40 created** — TKB refresh and re-embedding pipeline |
| `3ee39ab` | **Task 41 created** — admin knowledge curation |
| `31cf936` | Task 14 — `Flux<LlmEvent>` sealed union, superseding PLAN §5.1 |
| `ad49c85` | Tasks 06, 18 — `expected_version` / `version_conflict`, no optimistic UI on agent-mutable entities |
| `6b8cf18` | Tasks 09, 10, 12 — token-version revocation and the account-linking pre-hijack fix |
| *(this)* | Index, ledger, and dependency updates |

Tasks 07, 08, 11, 15, 20, 22, 26, 28, 31 and 33 already referenced the relevant ADR concepts and
were left unchanged — the reconciliation was deliberately surgical rather than a blanket rewrite.

**The original finding, retained for context:**

ADRs 006–010 landed in `aa20043`, **after** the 40 briefs were written (`769fa90`, `c02c9e2`).
Their `Consequences` sections name the tasks they affect, but no brief was updated. Authority
order (§3) puts an accepted ADR at rank 3 and a task brief at rank 6, so an agent following a
brief will build something an ADR forbids — and the conflict protocol then requires it to stop.
The practical effect is that affected tasks stall at execution time instead of at review time.

**Task 17 is the sharpest case:** its Scope asks for a "realistic `StubDestinationKnowledgeAdapter`",
which ADR 010 §3 forbids outright (production must fail to start if one is wired; development
must show a `sample_data` banner). `docs/AGENT-HARNESS.md` §7 already flags this as a hazard, so
the harness catches it — but only by halting the task.

Mandated by an accepted ADR and absent from every brief:

| Missing item | Source | Belongs to |
|---|---|---|
| `destination.coverage_level`, typed `destination_not_covered`, `GET /destinations/supported` | ADR 010 §4 | 16, 17 |
| Licence register (`licence`, `attribution_text`) and attribution in the UI | ADR 010 §2 | 16, 17 |
| `content_hash` re-embed trigger, additive model-migration path | ADR 010 §5 | 16, 17 |
| Freshness TTLs by data class, `stale: true` propagation | ADR 010 §6 | 17 |
| `Flux<LlmEvent>` sealed event union, `Usage` → `ai_call_log` | ADR 007 | 14 |
| `expected_version` / `409 version_conflict` in the error catalog | ADR 008 | 06, 18 |
| `token_version`, `sessions_valid_after`, refresh rotation, logout-all | ADR 009 | 09, 12 |
| Account-linking pre-hijack rules (verified-existing-account, GitHub primary+verified, Firebase `aud`) | ADR 009 §4 | 10 |

**Two tasks required by ADR 010 do not exist at all** — its own Consequences section says so:
the TKB refresh / re-embed pipeline, and admin knowledge curation. Neither appears among tasks
00–39.

**Resolution applied:** the ADR consequences were reconciled into the affected briefs and the two
missing tasks were created. No ADR was edited to match a brief — the ADR is the higher authority,
so the briefs moved.

**Standing rule this produced:** when an ADR is accepted, its `Consequences` section names the
tasks it affects. Those briefs must be updated in the same change. An ADR that outruns its briefs
does not fail loudly — it stalls a task months later, at execution time.

### B-1 — Local toolchain below required versions — ✅ RESOLVED 2026-07-25

Originally raised because the machine ran Node 18.19.1 and Java 1.8.0_51 with no JDK 21 present,
blocking Tasks 02, 03 and 04. Resolved by switching Node via `nvm` and installing Temurin 21.

| Tool | Required | Detected | Status |
|---|---|---|---|
| Node.js | 22.x | 22.23.1 (`nvm`) | ✓ |
| npm | 10+ | 10.2.4 | ✓ |
| Java JDK | 21 | 21.0.11 LTS — Temurin `21.0.11+10` | ✓ |
| `JAVA_HOME` | JDK 21 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\` (machine scope) | ✓ |
| Docker | v2+ | 28.5.2 | ✓ |
| Docker Compose | v2+ | v2.40.3-desktop.1 | ✓ |
| Git | 2.x+ | 2.40.1.windows.1 | ✓ |

Verification in a shell with freshly-resolved environment:

```
C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe
openjdk version "21.0.11" 2026-04-21 LTS
OpenJDK Runtime Environment Temurin-21.0.11+10 (build 21.0.11+10-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.11+10 (build 21.0.11+10-LTS, mixed mode, sharing)
javac 21.0.11
```

The Adoptium `bin` directory precedes both Oracle `javapath` shims in the machine `PATH`, so
`java` resolves to 21 in any new shell. Tasks 02, 03 and 04 are unblocked.

> **Note — JDK 21 not 22.** JDK 22 was requested but 22 is a non-LTS release, out of support since
> September 2024, and `plans/superpower/PLAN.md` §4.0.0, `README.md`, and `docs/adr/001-gradle.md`
> all lock **Java 21**. Installing 22 would have failed the very gate Task 01 builds. Confirmed
> with the user before installing (see follow-up **F-4** for the leftover stale `PATH` entry).

### B-2 — Trunk branch ambiguity — ✅ RESOLVED 2026-07-25 (user decision)

> **`master` is the trunk. All implementation branches start from `master` and target `master`.
> `main` is out of use — do not branch from it, merge to it, or update it.**

This matches `tasks/README.md`, `AGENTS.md`, every task brief, and `plans/superpower/PLAN.md`
§"`master` — protected; PR required; CI green". No documentation change was required.

`main` was verified to be a stale snapshot, not a divergent line of work: its tree is byte-identical
to commit `533ba25`, an ancestor on `master`'s own history, and it contains only older copies of
`README.md` and `plans/superpower/PLAN.md`. Both files exist in newer form on `master`, so nothing
is lost by leaving `main` behind.

| Branch | Role |
|---|---|
| `dev` | **Working trunk** — see the supersession note below. |
| `master` | Baseline at `aa20043`; `dev` carries all implementation work forward from it. |
| `main` | **Abandoned.** Stale snapshot, 38 commits behind. Not maintained. |

> **Superseded 2026-07-26 (user decision): implementation work happens on `dev`.**
> The `agent/task-NN-*` branch-per-task rule from `tasks/README.md` is not being followed;
> commits land directly on `dev`, one commit per feature. Tasks 00 and 01 were created on
> `agent/task-00-plan-baseline` / `agent/task-01-root-tooling` before this decision and were
> fast-forwarded into `dev`. `master` is untouched since `aa20043`.
> This contradicts `tasks/README.md` §"Execution rules" — recorded rather than silently
> diverging. Reconciling the two (or promoting `dev` to trunk in the docs) is follow-up **F-8**.

If the GitHub repository's default branch is still set to `main`, the landing page will show the
old two-file snapshot rather than the plan set. Changing that default is a repository setting the
owner applies directly; it does not affect local execution.

### B-3 — Open PRs not reviewed through the GitHub API

`gh pr list` fails with `To get started with GitHub CLI, please run: gh auth login`. Substituted
with the exhaustive remote-branch comparison in §2, which found no ref ahead of `master` other than
the stale `main`. **Resolution:** authenticate `gh`, or confirm no PRs are open.

## 8. Follow-ups (non-blocking)

| ID | Item |
|---|---|
| **F-1** | `tasks/README.md` baseline commit still reads `50f5d01`; should be updated to `aa20043` |
| **F-2** | Baseline validation (link/ID/cycle checks) was run from a throwaway script. Consider promoting it to `scripts/` under Task 01 or Task 05 so the ledger stays verifiable |
| **F-3** | 23 briefs lack a `Validation` section; the §6 universal gate covers this, but per-brief validation would be stronger |
| **F-4** | Stale user-`PATH` entry `C:\Program Files\Java\jdk-18.0.1.1\bin` remains. Harmless — machine `PATH` puts JDK 21 ahead of it — but worth removing to avoid confusion |
| **F-5** | `dev` mirrors `master` exactly and has no role under the B-2 decision. Delete it, or define its purpose, before it drifts |
| **F-6** | 11 fully-merged `origin/cursor/*` branches are absorbed into `master` and can be pruned |
| ~~F-7~~ | ✅ **Closed 2026-07-26.** The prerequisite gate now executes on `ubuntu-latest` in CI and passes, alongside `windows-latest`. The bash-builtins rewrite, the `gradlew`/`*.sh` `eol=lf` rule, and the `100755` exec bit were all validated together by that run |
| ~~F-13~~ | ✅ **Closed by task 08** (`801759c`). `PLAN.md` §4.0.5 declared `UserContext` with a Jakarta `@NotNull`, while §4.0.2-F forbids framework imports in the domain and task 07 places `UserContext` there. Task 07 kept PLAN's shape but moved the null check into the compact constructor, so the domain stays pure. The PLAN snippet should drop the annotation |
| ~~F-14~~ | ✅ **Closed by task 08** — user chose the per-request lookup. `UserContext` now carries `emailVerified`, read from current state on every authenticated request. Original finding:  [ADR 009](../docs/adr/009-session-lifecycle-revocation.md) §2 calls its absence a gap — UC-A08 blocks the planner until the email is verified, so the gate needs either a per-request lookup or a claim that goes stale. PLAN §4.0.5 defines the shape without it and outranks the ADR, so task 07 did **not** add it |
| **F-15** | `PLAN.md` §8 names the table `user`, which is a SQL reserved word requiring double-quoting in every statement and in `@Table`. Task 07 followed the plan and quoted it. `app_user` would remove a permanent footgun, but that is a PLAN change |
| **F-16** | `PLAN.md` §4.0.2-K specifies `src/test/integration/`, which is not Gradle's `src/integrationTest/` convention. Task 07 honoured the plan's path with a source set named `integrationTest`. Worth confirming the long-term choice |
| **F-17** | `PLAN.md` §4.0.5's endpoint table still lists `POST /auth/refresh` as "optional v1.1" and omits `/auth/logout-all`, while [ADR 009](../docs/adr/009-session-lifecycle-revocation.md) §3/§5 make both mandatory for v1 and state the table "is the spec". Task 08 implemented per ADR 009 and left the table alone. The table should be updated |
| **F-18** | **Browser auth will not work until the ADR 006 proxy lands.** `NEXT_PUBLIC_API_BASE_URL` is still the cross-origin `http://localhost:8080/api/v1`; ADR 006 requires a same-origin `/api/v1` plus a Next.js `rewrites()` proxy. With `SameSite=Lax` the session cookie will not be sent cross-origin. Owned by [Task 11](11-frontend-platform-auth-ui.md) |
| **F-19** | [ADR 009](../docs/adr/009-session-lifecycle-revocation.md) §6 says lockout is "keyed on both username and client IP", ambiguous between a composite key and two independent counters. Task 08 used a composite, following the ADR's own targeted-DoS rationale. Worth a one-line ADR clarification |
| **F-10** | **Error-envelope field count conflict.** `PLAN.md` §6.1 (authority 1) locks `{ code, message, details }`; [ADR 007](../docs/adr/007-chat-streaming-transport.md) (authority 3) describes the SSE terminal error frame as `{ code, message, details, request_id }`. Task 06 implemented PLAN's 3-field envelope for HTTP and exposes correlation via the `X-Request-Id` header. [Task 20](20-conversation-sse.md) must decide whether the SSE frame carries a 4th field — that is a PLAN/ADR reconciliation, not a task decision |
| **F-11** | `docs/AI-AGENT-WORKFLOW.md` §Step 2 shows OpenAPI path keys including the full `/api/v1/...`. Task 06 used `servers: /api/v1` with bare path keys, because the committed `NEXT_PUBLIC_API_BASE_URL` already ends in `/api/v1` and full path keys would double the prefix. PLAN §6.1 is satisfied either way; the workflow doc snippet should be updated to match |
| **F-12** | `api/openapi/*.yaml` sits under `src/main/java` because PLAN §4.0.0.1 locks that path, but Gradle does not put non-`.java` files there on the classpath, so the spec tests read it from the project directory rather than the classpath. Works, but moving it to `src/main/resources` would be cleaner and requires a PLAN change |
| **F-9** | macOS remains unexecuted — GitHub offers no free macOS runner here. macOS ships **bash 3.2**, so the risk is non-zero, though `check-prerequisites.sh` deliberately avoids bash 4+ features (no associative arrays, no `${var,,}`). Add a `macos-latest` matrix entry if macOS becomes a supported dev platform |
| **F-8** | `tasks/README.md` still mandates one branch/PR per task against `master`; actual practice is feature commits on `dev`. Reconcile the docs or the practice |

## 8a. Environment decisions (dev machine)

| Item | Decision |
|---|---|
| **Host ports** | Oracle XE's APEX endpoint owns `8080`. `docker-compose.yml` publishes `${BACKEND_HOST_PORT:-8080}`, `${FRONTEND_HOST_PORT:-3000}`, `${POSTGRES_HOST_PORT:-5432}`. Committed defaults stay exactly as PLAN §4.0.0 locks them; the untracked local `.env` sets `8081`. `NEXT_PUBLIC_API_BASE_URL` must track the host port and is inlined at **build** time — changing it needs `docker compose build frontend`. |
| **Docker Desktop** | Docker Model Runner ("Docker AI") must stay disabled on this machine: it builds an unquoted `unix://C:\Users\Voo Yi Sen\...` socket path and crashes at startup because the username contains spaces. Re-check after any Docker Desktop update or reinstall. |

---

## 9. Next executable task

> **[Task 06 — OpenAPI and Error Platform](06-openapi-error-platform.md)**

**Phase 0A (tasks 00–05) is complete and proven in CI**, not merely locally: all five jobs are
green on GitHub Actions, including the Docker smoke test reaching `"database":"UP"` in a clean
runner. Nothing is blocked — B-1, B-2 and B-4 are resolved, F-7 is closed, and B-3 is
informational.

Task 06 has been reconciled with ADR 008 (`expected_version`, `version_conflict`) and ADR 007
(chat paths excluded from the codegen-drift gate) as part of the B-4 resolution, so it can be
executed from its brief without further reconciliation.
