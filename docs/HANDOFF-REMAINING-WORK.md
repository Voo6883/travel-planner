# Handoff — remaining work and how to execute it

> Updated 2026-08-04 after task 24 ranking DSA (branch `cursor/task-24-destination-ranking-15b0`).
>
> This document does **not** replace [`AGENTS.md`](../AGENTS.md),
> [`docs/AGENT-HARNESS.md`](AGENT-HARNESS.md), [`docs/AI-AGENT-WORKFLOW.md`](AI-AGENT-WORKFLOW.md)
> or [`tasks/README.md`](../tasks/README.md) — read those first, in that order.

---

## 1. Where the project is

`dev` is the working branch. **Next free migration: `V25`.**

| Phase | Tasks | State |
|---|---|---|
| 0A/0B — foundation + platform | 00–15 | `done` |
| Phase 1 — knowledge, intake, chat | 16–24 `done` · 17 `done_with_accepted_debt` (F-34→41) | see §2 |
| Phase 1 — research, itinerary | 25–31 | not started — **25 next** |
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
| **22** Trip chat intake tools | 18–21 `done` | **done** — merged to `dev` |
| **23** Research job platform | 07, 18, 22 `done` | **done** — migration **V24** |
| **24** Destination ranking | 17A/17B, 18, 23 | **done** — pure DSA; see [`DESTINATION-RANKING-HANDOFF.md`](DESTINATION-RANKING-HANDOFF.md) |
| **25** Travel research agent | 14, 17, 23, 24 | Next — plugs into research job hooks; calls `DestinationRanker` |
| **40** TKB refresh | 14–16 + gates **17A/17B** | Safe parallel while **17C**/F-34 remains open on **41** |

### Still open on task 17

Gate **17C** — real curation for the first three destinations (**F-34**). Owned by task **41**.
Does not block 24–26 (gates **17A/17B** are `done`).

---

## 3. Traps that already cost time

- Migration numbers: `surprise_me` is **V23**; `research_job` is **V24**. **Next is V25.**
- Streaming AI: use `application.ai.LlmStreamPort`, not `domain.port.LlmPort` for Flux.
- Do not replace `ChatReplay` / gate 20C with an ADR-only “no replay” shortcut.
- Research no-op hooks: register via `ResearchExecutionConfig` `@Bean` +
  `@ConditionalOnMissingBean` — not scanned `@Component` + `@ConditionalOnMissingBean`.
- Ranking is pure domain DSA — application loads bounded `DestinationCandidate`s; never ask an
  LLM for `fitScore`.
- No Docker in some Cloud VMs — unit build with `DOCKER_HOST=tcp://127.0.0.1:1`; IT via
  `npm run test:integration` when Postgres is local.

---

## 4. Authority

When docs conflict: stop and ask (`docs/AGENT-HARNESS.md` §3). PLAN.md wins on architecture.
