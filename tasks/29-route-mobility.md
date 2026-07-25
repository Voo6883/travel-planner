# Task 29 — Route and Mobility Planning

## Objective

Implement grounded route-leg resolution, transport selection, and locale-app recommendations used between itinerary items.

## Dependencies

- Tasks 17 and 28 complete.

## Required reading

- `plans/TRAVEL-KNOWLEDGE-CATALOG.md` mobility, route segment, and travel-app sections
- `plans/USE-CASES.md` UC-C3-09–C3-12 and UC-K09–K14
- `plans/BACKLOG.md` mobility portion of S5-2

## Scope

- Application service using `KnowledgePort.findRoutes`, transport modes, POI/area/location data, and app packs.
- Resolve each A→B leg into mode, duration, cost band where known, instructions, source/route-segment ID, and recommended local apps.
- Deterministic choice policy based on available modes, time, mobility/pace constraints, cost, and locale rules.
- China/local-app replacement enforcement and app selection by leg mode.
- Explicit fallback for missing routes: area-level estimate, unknown leg requiring user attention, or configured live provider adapter when available; never fabricate precision.
- Route chain consistency and insertion into itinerary legs.
- `get_route` read application service/tool-ready interface.
- Contract tests shared by stub/static and future live adapters.
- Tests for walking, metro/train, ride-hail, ferry, missing segment, conflicting/stale data, local-app replacement, and source attribution.

## Do not

- Do not implement unrestricted all-pairs routing data.
- Do not claim real-time accuracy for static fixtures.
- Do not let the LLM choose unsupported modes or apps.
- Do not implement UI/chat integration owned by Tasks 30–31.

## Definition of Done

- Every resolvable itinerary gap has a source-linked leg.
- Missing route information is explicit and safe.
- Local transport/app rules are deterministic and tested.
- Route service is ready for itinerary generation and chat queries.

## Handoff

Document resolution priority, fallback semantics, route freshness, provider interface, and leg schema.

## Suggested branch and commit

- Branch: `agent/task-29-route-mobility`
- Commit: `feat: implement grounded route and mobility planning`
