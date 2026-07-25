# Task 30 — Itinerary Generation Agent

## Objective

Implement C3 itinerary generation as a grounded, structured AI workflow whose output must pass deterministic scheduling and route validation before persistence.

## Dependencies

- Tasks 14, 26, 28, and 29 complete.

## Required reading

- `plans/superpower/PLAN.md` C3 and structured-output rules
- `plans/USE-CASES.md` UC-C3-01–C3-12
- `plans/BACKLOG.md` AI portion of S5-2 and S5-5
- `docs/AI-AGENT-WORKFLOW.md` AI addendum

## Scope

- `generate_itinerary` application/agent workflow allowed only when `trip.status=DESTINATION_SELECTED`.
- Retrieve selected recommendation, brief, POIs, areas, opening hours, visit durations, mobility, route facts, local apps, and provenance through application/knowledge services.
- Versioned prompt and typed candidate itinerary output; every POI must resolve to a known ID/source.
- Use LLM for selection/narration/sequence proposal, then Task 28 scheduler and Task 29 route service for authoritative feasibility.
- Bounded repair loop for validation failures with explicit maximum attempts; fail with typed reason when no valid plan can be produced.
- Persist validated itinerary, legs, provenance, prompt/model metadata, and transition to `ITINERARY_READY` in a short transaction.
- Regeneration semantics: define whether it creates a version, replaces draft, and preserves user-approved constraints.
- `patch_itinerary` structured-diff command/schema foundation for Task 31; tool name does not imply HTTP PATCH.
- Golden/evaluation cases for duplicate POIs, impossible hours, excessive load, missing routes, fabricated IDs, budget/pace mismatch, provider timeout, and malformed output.

## Do not

- Do not persist unvalidated model output.
- Do not invent POIs, opening hours, routes, prices, or apps.
- Do not call the LLM or route tools inside transactions.
- Do not implement UI or free-text destructive replacement.

## Definition of Done

- Valid inputs produce a feasible, sourced itinerary or typed failure.
- Scheduler/route validators remain authoritative.
- Tool/model loops are bounded and observable.
- Successful persistence atomically transitions Trip status.

## Handoff

Document prompt/schema version, repair loop, itinerary version/regeneration semantics, patch command, and output consumed by Task 31.

## Suggested branch and commit

- Branch: `agent/task-30-itinerary-agent`
- Commit: `feat: generate validated grounded itineraries`
