# Task 28 — Itinerary Domain and Scheduling

## Objective

Implement the deterministic itinerary model and scheduling rules that ensure generated plans are feasible before LLM narration.

## Dependencies

- Tasks 17, 18, and 26 complete.

## Required reading

- `plans/USE-CASES.md` C3
- `plans/TRAVEL-KNOWLEDGE-CATALOG.md` timeline and itinerary entities
- `plans/superpower/PLAN.md` DSA/itinerary sections
- `plans/BACKLOG.md` S5-1 and deterministic portion of S5-2

## Scope

- Migrations/domain/persistence for itinerary aggregate, day, item, and leg references, including optimistic versioning/history needs for later edits.
- Typed scheduled start/end, duration, date/day number, area focus, POI/source, item category, notes, and meal/free-time concepts.
- Pure scheduling/validation algorithm for daily windows, no overlaps, visit duration, opening hours when known, travel-buffer inputs, meal slots, area clustering, pace, and maximum load.
- Deterministic selection/order helpers where justified, with clear separation from route lookup (Task 29) and LLM narrative (Task 30).
- Typed infeasible/partial/unknown-data outcomes; never silently ignore constraints.
- Persistence service using short transactions and conflict handling.
- Table-driven tests for overlaps, closed POIs, missing hours, single/multi-day, timezone, daylight/date boundaries, duplicates, pace, and impossible plans.

## Do not

- Do not call an LLM or route provider.
- Do not invent opening hours/travel times.
- Do not expose persistence entities.
- Do not implement frontend timeline yet.

## Definition of Done

- Itinerary structures and constraints are deterministic and tested.
- Invalid schedules cannot be persisted as ready.
- Unknown source data is explicit.
- Task 30 can submit candidate plans for validation through a stable interface.

## Handoff

Document itinerary schema, scheduling command/result, invariants, algorithm complexity, timezone policy, and required route inputs.

## Suggested branch and commit

- Branch: `agent/task-28-itinerary-scheduling`
- Commit: `feat: add itinerary domain and scheduler`
