# Handoff — remaining work and how to execute it

> Updated 2026-08-04 after gates 18B/19B and task 21 merged to `dev` (`0c6f343`).
>
> This document does **not** replace [`AGENTS.md`](../AGENTS.md),
> [`docs/AGENT-HARNESS.md`](AGENT-HARNESS.md), [`docs/AI-AGENT-WORKFLOW.md`](AI-AGENT-WORKFLOW.md)
> or [`tasks/README.md`](../tasks/README.md) — read those first, in that order.

---

## 1. Where the project is

`dev` is the working branch. **Next free migration: `V24`.**

| Phase | Tasks | State |
|---|---|---|
| 0A/0B — foundation + platform | 00–15 | `done` |
| Phase 1 — knowledge, intake, chat | 16 `done` · 17 `in_progress` (17C only) · 18–21 `done` · **22–23 done** · **17 done_with_accepted_debt (F-34→41)** · **24 next** | see §2 |
| Phase 1 — research, itinerary | 23–31 | not started |
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
| **23** Research job platform | 07, 18, 22 `done` | **done** |
| **24** Destination ranking | 17A/17B, 18, 23 | Pure DSA fitScore; no LLM scores |
| **40** TKB refresh | 14–16 + gates **17A/17B** | Safe parallel with 22–26 while **17C** remains open |

### Still open on task 17

Gate **17C** — real curation for the first three destinations (**F-34**). Does not block 22.

---

## 3. Traps that already cost time

- Migration numbers: `dev` owns **V21** (`travel_app_replacement`) and **V22** (POI fulltext
  `english`). `surprise_me` is **V23**. Next is **V24**.
- Streaming AI: use `application.ai.LlmStreamPort`, not `domain.port.LlmPort` for Flux.
- Do not replace `ChatReplay` / gate 20C with an ADR-only “no replay” shortcut.
- Chat tests must wire `PlannerChatOrchestrator` (and soon `TripChatOrchestrator`) via
  `ChatTestFakes` / constructors — bare `LlmStreamPort` is not enough after task 21.
- Frontend navigates only on typed SSE `trip_created`; brief form sync must invalidate
  `queryKeys.trips.brief(tripId)`.
- No Docker in some Cloud VMs — unit build with `DOCKER_HOST=tcp://127.0.0.1:1`; IT via
  `npm run test:integration` when Postgres is local.

---

## 4. Authority

When docs conflict: stop and ask (`docs/AGENT-HARNESS.md` §3). PLAN.md wins on architecture.
