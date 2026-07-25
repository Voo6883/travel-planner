# Execution Baseline — Travel Planner Implementation

> Deliverable of [Task 00](00-plan-baseline.md). This is the execution-control layer for all
> implementation work. It contains **no application code** and creates no scaffold.
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
| Application scaffold present | **No** — see below |
| Working tree | Clean |

### Supersedes the commit recorded in `tasks/README.md`

`tasks/README.md` records base commit `50f5d01ae3751bd84e906c530d99175e9f84fda5`. That commit is a
valid ancestor of `master` but is no longer the tip: `aa20043` has since added `tasks/` (the 40 task
briefs) and `docs/AGENT-HARNESS.md`. **`aa20043` is the authoritative baseline for all Phase 0A
work.** `tasks/README.md` is left unedited — amending it is outside this task's scope and is
recorded as follow-up **F-1**.

### No application scaffold confirmed

```
$ ls package.json docker-compose.yml apps .nvmrc scripts
ls: cannot access 'package.json': No such file or directory
ls: cannot access 'docker-compose.yml': No such file or directory
ls: cannot access 'apps': No such file or directory
ls: cannot access '.nvmrc': No such file or directory
ls: cannot access 'scripts': No such file or directory
```

The repository contains planning documentation only. Phase 0A starts from zero, as
`AGENTS.md` and `docs/PLAN-COMPATIBILITY.md` §"Kickoff readiness" both assert.

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
| `master` | **Trunk.** Protected; PR required; CI green before merge. |
| `agent/task-NN-*` | One per task brief. Branch from `master`, merge to `master`. |
| `dev` | Currently an exact mirror of `master`. No defined role — see follow-up **F-5**. |
| `main` | **Abandoned.** Stale snapshot, 38 commits behind. Not maintained. |

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
| **F-7** | Task 01's prerequisite gate is unexecuted on Linux/macOS — no host available. Bash builtins only, so it should hold, but [Task 05](05-ci-repository-workflow.md) must run `npm run prereq` and `npm run prereq:test` on an Ubuntu runner to close this |

---

## 9. Next executable task

> **[Task 01 — Prerequisite and Root Tooling](01-prerequisite-root-tooling.md)** — branch
> `agent/task-01-root-tooling`.

Task 00's only dependent is Task 01, and Task 00 is `done`. Nothing blocks it: B-1 (toolchain) and
B-2 (trunk) are both resolved, and B-3 does not gate execution. Branch from `master`, merge to
`master`.
