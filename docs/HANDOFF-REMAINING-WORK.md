# Handoff — remaining work and how to execute it

> Updated 2026-08-04 after tasks 25–27 landed on `dev` (@ `3d58abf`) and the full verification
> ladder was re-run green — see §1.
>
> This document does **not** replace [`AGENTS.md`](../AGENTS.md),
> [`docs/AGENT-HARNESS.md`](AGENT-HARNESS.md), [`docs/AI-AGENT-WORKFLOW.md`](AI-AGENT-WORKFLOW.md)
> or [`tasks/README.md`](../tasks/README.md) — read those first, in that order.

---

## 1. Where the project is

`dev` is the working branch. **Next free migration: `V27`.**

**Verified green on `dev` @ `3d58abf` (2026-08-04), the whole ladder:**

| Stage | Result |
|---|---|
| `npm run verify:task` | 9/9 steps passed — backend build + Checkstyle + ArchUnit + coverage, frontend format/lint/typecheck/coverage, codegen drift, CODE-MAP drift, generator tests, stale docs |
| `npm run test:integration` | **146 passed, 0 failed** against PostgreSQL 16.6 + pgvector 0.8.1 |
| `npm run build` (frontend) | production build clean, 16 routes |

Together these are `verify:full`. The two fixes immediately before this (`f8baa4e` score/`numeric`
alignment, `cfd4048` mail-listener split) were what unblocked the Spring IT contexts; nothing else
on `dev` is failing.

| Phase | Tasks | State |
|---|---|---|
| 0A/0B — foundation + platform | 00–15 | `done` |
| Phase 1 — knowledge, intake, chat | 16–24 `done` · 17 `done_with_accepted_debt` (F-34→41) · **25 done** | see §2
| Phase 1 — research, itinerary | 25–27 `done` · 28–31 | not started — **28 next**
| Phase 2 — booking, runtime | 32–37 | not started |
| Knowledge ops | 40, 41 | not started |
| Integration | 38, 39 | not started |

[`tasks/STATUS.md`](../tasks/STATUS.md) is the single source of truth for status and carries the
open-question register (`F-nn`). Update it in the same commit as the work, never afterwards.

---

## 2. Execution order

### Critical path now

```
22 → 23 → 24 → 25 → 26 → 27 ┐
                  26 → 28 → 29 → 30 → 31 ┤
                                         ├→ 35 → 36 → 37 → 38 → 39
                         32 → 33 → 34 ───┘
```

| Task | Needs | Notes |
|---|---|---|
| **22–27** | — | **done** on `dev` |
| **25** Travel research agent | 14, 17, 23, 24 | `done` — migration **V25**; see [`TRAVEL-RESEARCH-AGENT-HANDOFF.md`](TRAVEL-RESEARCH-AGENT-HANDOFF.md) |
| **26** Research API + frontend | 11, 23–25 | `done` — see [`RESEARCH-EXPERIENCE-HANDOFF.md`](RESEARCH-EXPERIENCE-HANDOFF.md) |
| **27** Research chat + evaluation | 22, 25, 26 | `done` — migration **V26**; see [`RESEARCH-CHAT-EVAL-HANDOFF.md`](RESEARCH-CHAT-EVAL-HANDOFF.md) |
| **28** Itinerary domain + scheduling | 17, 18, 26 | **Next** — depends on **F-29** (`Destination.timezone` is never validated as an IANA zone) |
| **40** TKB refresh | 14–16 + gates **17A/17B** | Safe parallel while **17C**/F-34 remains open on **41** |

### Still open on task 17

Gate **17C** — real curation for the first three destinations (**F-34**). Owned by task **41**.
Does not block 28 (gates **17A/17B** are `done`). Sample data stays PARTIAL — research correctly
persists typed `no_confident_result`.

---

## 3. Traps that already cost time

- Migration numbers: `surprise_me` is **V23**; `research_job` is **V24**; recommendations are
  **V25**; `completion_mail_sent_at` is **V26**. **Next is V27.**
- Streaming AI: use `application.ai.LlmStreamPort`, not `domain.port.LlmPort` for Flux.
- Do not replace `ChatReplay` / gate 20C with an ADR-only “no replay” shortcut.
- Research no-op hooks: real beans are scanned `@Service`/`@Component` so they win over
  `ResearchExecutionConfig` `@Bean` + `@ConditionalOnMissingBean` no-ops — do **not** edit the
  NoOp* classes.
- Ranking is pure domain DSA — never ask an LLM for `fitScore`.
- `RESEARCH_READY` without a `research_run_result` row is a bug — task 25 stages via
  `PendingResearchOutcomeStore` + completion hook.
- No Docker in some Cloud VMs — unit build with `DOCKER_HOST=tcp://127.0.0.1:1`; IT via
  `npm run test:integration` when Postgres is local.

---

## 4. Authority

When docs conflict: stop and ask (`docs/AGENT-HARNESS.md` §3). PLAN.md wins on architecture.
