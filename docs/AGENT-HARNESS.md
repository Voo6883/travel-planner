# Agent Harness — Scope Control & Drift Prevention

> **Read this before `docs/AI-AGENT-WORKFLOW.md`.**
> That doc says *how* to build. This doc says *what you are allowed to build, and when to stop.*
>
> Order per session: **`AGENTS.md` → this file → `tasks/NN-*.md` → `docs/AI-AGENT-WORKFLOW.md`**

This harness exists because the repo is documentation-dense (~7,500 lines across 50+ files) and
executed largely by AI agents. The dominant failure mode is not bad code — it is **drift**:
building the wrong thing, building beyond the work order, or silently resolving a documentation
conflict by guessing.

---

## 1. Topic boundary (what this product is)

**Travel Planner is a knowledge-grounded, chat-first trip planner.** A user converses with an
LLM; the system retrieves facts from the Travel Knowledge Base (TKB), reasons over them, and
produces a grounded brief → ranked destinations → day-by-day itinerary → human-confirmed bookings.

| In scope | Out of scope (do not build without a new task + ADR) |
|---|---|
| C1 intake · C2 research · C3 itinerary · C4 booking · C5 chat | Any feature not traceable to C1–C5 or an approved C6+ slice |
| Travel domain: destinations, POIs, food, areas, routes, transport, travel apps | Generic CRUD, CMS, dashboards, or analytics products |
| User-owned trips, one user per trip | Multi-tenant, orgs, teams, workspaces, role hierarchies beyond `USER`/`ADMIN` |
| Grounded answers with `source_refs[]` | Free-form LLM travel advice with no KB provenance |
| Agent **proposes**; user confirms | Any autonomous purchase, payment, or booking commit |

**The topic test.** Before writing code, answer in one sentence: *"Which of C1–C5 does this serve,
and which `UC-*` acceptance criterion does it satisfy?"* If you cannot, you are off-topic — stop
and ask.

---

## 2. The work order

**No code without a task ID.** `tasks/NN-*.md` is the only valid work order. A request, an idea in
a plan section, or something you noticed while reading is **not** a work order.

| Rule | Detail |
|---|---|
| One task per branch/PR | Branch `agent/task-NN-*` exactly as the brief states |
| The brief's `Scope` is a ceiling, not a floor | Implement all of it; implement none of what it omits |
| The brief's `Do not` list is absolute | These encode safety and architecture invariants |
| Dependencies are gates | Do not start a task whose dependencies are not `done` in the Task 00 ledger |
| Found a real problem outside scope? | Record it as a blocker/follow-up. Do **not** fix it in this PR |

**Minimal-diff rule.** If a file is not required by the task's `Scope`, do not touch it. Drive-by
refactors, renames, dependency bumps, and formatting sweeps are drift.

---

## 3. Authority order & conflict protocol

When two documents disagree, the higher authority wins:

1. `plans/superpower/PLAN.md` — architecture, data flow, product behavior
2. `docs/UI-UX-DESIGN-SYSTEM.md` — visual tokens, responsive, accessibility
3. `docs/adr/` — accepted one-way decisions
4. `plans/USE-CASES.md` — acceptance criteria
5. `plans/BACKLOG.md` — delivery sequencing
6. `tasks/NN-*.md` — execution-sized decomposition (may clarify, must not silently override 1–5)

**Conflict protocol — the single most important rule in this file:**

> When documents conflict, or a required decision is genuinely unspecified:
> **stop, record the conflict, and ask.** Do not choose an undocumented interpretation and
> proceed. An agent's guess becomes a locked decision nobody reviewed.

Resolution happens through a documentation/ADR change **before** coding resumes.

---

## 4. Drift tripwires

Stop and re-read the task brief when any of these become true:

| Tripwire | Why it signals drift |
|---|---|
| You are editing a file no `Scope` bullet named | Scope creep |
| You are writing a class for a feature two tasks ahead | Building without a work order |
| You are about to add a dependency, service, or table not in the brief | Architecture change without ADR |
| You are resolving a doc contradiction on your own judgment | See §3 — must stop |
| You are writing a `Stub*Adapter` for the **Knowledge** port | Stubbed KB emits invented facts with fake provenance — violates §4.1.0 |
| You are about to let a model call a state-mutating tool without validation | Tool args must be schema-validated; never execute from free text |
| You are hand-writing a frontend API type | Must come from OpenAPI codegen |
| Your diff exceeds the brief's evident size with no explanation | Bundled work — split the PR |
| You are "fixing while you're in there" | Minimal-diff violation |

---

## 5. Scope contract (fill before writing code)

Paste into the PR description and complete **before** the first edit:

```
Task ID:            tasks/NN-*.md
Feature (C1–C5):    ______   Acceptance criteria (UC-*): ______
Dependencies met:   ______ (per Task 00 ledger)
Files I will touch: ______
Explicitly NOT doing (from the brief's "Do not"): ______
Conflicts/unknowns found: ______  → blocked? yes/no
```

If "Conflicts/unknowns" is non-empty and unresolved, the correct next action is **ask**, not code.

---

## 6. Evidence gate

23 of 40 task briefs have no `Validation` section. Until that is fixed, **this gate applies to
every task regardless of the brief**:

- [ ] The task's `Definition of Done` is quoted, item by item, with evidence for each
- [ ] Commands actually run, with **real output pasted** — never "tests should pass"
- [ ] Backend touched → `./gradlew test` (plus lint/arch/coverage once Task 15 lands)
- [ ] Frontend touched → build + typecheck + tests on Node 22
- [ ] Contract touched → codegen re-run; no drift
- [ ] LLM output touched → schema/golden-file test
- [ ] The §5 scope contract is complete and honest about what was skipped

**Never report a task complete on generated code alone.** Evidence is required
(`tasks/00-plan-baseline.md`). If something was skipped or failed, say so plainly — a truthful
partial report is correct; an optimistic one corrupts every downstream task.

---

## 7. Known documentation hazards

Verified inconsistencies. **Do not "fix" these inline** — they are recorded here so you don't
follow a stale instruction. Raise them as blockers.

| Hazard | Correct behavior |
|---|---|
| `docs/ADDING-A-FEATURE.md:25` says i18n `en/` only | Locales are **`en` + `ms`** (UI-UX §14) |
| Three different verify commands across docs/ADRs | Use the command in **your task brief**; if absent, ask |
| `docs/ADDING-A-FEATURE.md:158` hard-codes JaCoCo ≥70% | Thresholds are set by `tasks/15-quality-gates.md` |
| `patch_itinerary` in diagrams/plan | LLM **tool name only** — HTTP `PATCH` is forbidden (§6.1) |
| ADR 002 hedges cookie name / "OAuth needs new ADR" | Superseded by **ADR 004** |
| UI states listed as loading/error/empty | PWA also requires **offline** + disabled/focus states |
| Frontend "attach JWT in client.ts" | Impossible — httpOnly cookie; use `credentials: 'include'` + CSRF header (ADR 006) |
| ArchUnit snippet in PLAN §4.0.9 | Does not run as written — treat as illustrative, not copy-paste |
| PLAN §4.0.5 "frontend calls Spring Boot directly" | Amended by **ADR 006** — same-origin rewrite proxy |
| PLAN §5.1 `Flux<String> stream(...)` | Superseded by **ADR 007** — `Flux<LlmEvent>` |
| PLAN §6.1 full-body `PUT` for large aggregates | See **ADR 008** — typed POST actions + `expected_version` |
| ADR 002 "refresh strategy in v1.1 if needed" | Superseded by **ADR 009** — refresh + revocation are v1 |
| `StubDestinationKnowledgeAdapter` | **ADR 010** — forbidden in prod; dev-only with `sample_data` banner |

---

## 8. Escalate to the user when

- The task changes a locked decision (`PLAN.md` §1) or needs a new ADR
- Documents conflict, or a required contract is unspecified (§3)
- A new runtime service, external vendor, or dependency is needed
- A dependency task is incomplete, or the brief contradicts the plan
- `npm run prereq` fails
- The safe path and the fast path diverge — **always surface it rather than choosing**

Escalating is never a failure. Guessing is.
