# Task 31 — Itinerary UI and Chat Editing

## Objective

Deliver the complete C3 timeline experience and safe structured itinerary refinement through trip chat.

## Dependencies

- Tasks 22, 27, and 30 complete.

## Required reading

- `docs/UI-UX-DESIGN-SYSTEM.md`
- `plans/USE-CASES.md` C3 and UC-C5-06/C5-07
- `plans/superpower/PLAN.md` itinerary routes and `patch_itinerary` policy
- `plans/BACKLOG.md` S5-3, S5-4, S5-5

## Scope

### API/frontend

- Itinerary get/generate/regenerate endpoints and generated client integration.
- `features/itinerary/` vertical timeline by day with scheduled times, POI/food/free-time cards, source/freshness details, and route-leg rows showing mode, duration, instructions, cost band when known, and local app badges.
- Generate, retry, infeasible, conflict, stale-source, loading, empty, offline, and archived states.
- Responsive/mobile and keyboard-complete presentation.
- English/Malay translations.

### Chat tools

- `generate_itinerary` only in `DESTINATION_SELECTED`.
- `patch_itinerary` only in `ITINERARY_READY` or later allowed states.
- Parse user refinement into a structured diff such as move, remove, replace, add constraint, or regenerate day; validate against current itinerary version.
- Preview/confirm destructive or broad changes where needed.
- Apply through deterministic scheduling/route validation and optimistic locking.
- Summarize only committed changes and refresh the structured view.

### Tests

- Component tests for timeline/legs/sources and all states.
- Service/tool tests for invalid status, stale version, impossible patch, fabricated POI, and concurrent edit.
- Full C1→C2→C3 Playwright path using deterministic providers.

## Do not

- Do not replace the itinerary from unstructured model prose.
- Do not hide unknown route/source data.
- Do not show an edit as successful before server commit.

## Definition of Done

- User can generate, view, reload, and safely refine a sourced itinerary.
- Chat and timeline share one versioned server state.
- Invalid edits fail visibly without partial writes.
- Phase 1 end-to-end planning funnel passes.

## Handoff

Document route map, query keys, patch operations, confirmation policy, version/conflict handling, and booking inputs.

## Suggested branch and commit

- Branch: `agent/task-31-itinerary-experience`
- Commit: `feat: deliver itinerary UI and chat editing`
