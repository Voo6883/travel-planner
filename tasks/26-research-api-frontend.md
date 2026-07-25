# Task 26 — Research API and Frontend

## Objective

Expose C2 job/recommendation/selection APIs and build the complete structured research experience.

## Dependencies

- Tasks 23–25 complete.
- Task 11 frontend platform complete.

## Required reading

- `plans/USE-CASES.md` C2
- `plans/superpower/PLAN.md` C2 API and frontend rules
- `docs/UI-UX-DESIGN-SYSTEM.md`
- `plans/BACKLOG.md` S4-5, S4-6, S4-7

## Scope

### API/application

- Start research, get job, list ranked recommendations with `GET`, get destination guide/detail, re-run where planned, and select recommendation.
- Enforce user ownership and status gates.
- Selection persists the chosen recommendation/destination and transitions `RESEARCH_READY → DESTINATION_SELECTED` atomically.
- Return 409 typed errors when results are not ready or transitions conflict.
- Recommendation DTOs include rationale, estimated cost, score/breakdown as appropriate, traveler guide, risks, freshness, confidence, and source refs.

### Frontend

- `features/research/` query/mutation hooks and panels.
- Start CTA, queued/running progress, leave-and-resume polling, failure/retry, no-confident-result, ranked cards, guide sections, source/provenance display, cost/confidence/freshness, areas/food/highlights/practical/mobility, and local app pack.
- Explicit `Plan this trip` confirmation and selected state.
- English/Malay translations, keyboard/mobile behavior, loading/error/empty/offline states.
- Poll only while appropriate and stop on terminal states/unmount.

### Tests

- Contract/service/controller tests, transition/ownership/concurrency tests.
- Component tests for all states.
- E2E from `BRIEF_COMPLETE` to `DESTINATION_SELECTED` using stubs.

## Do not

- Do not select via optimistic UI before server confirmation.
- Do not hide or fabricate sources.
- Do not implement itinerary generation.

## Definition of Done

- User can start, leave, resume, inspect, and select research results.
- State transitions are authoritative and recoverable.
- Recommendation evidence is visible.
- C2 E2E is stable with deterministic fixtures.

## Handoff

Document API routes, query keys, polling rules, recommendation schema, selection semantics, and itinerary input for Tasks 28–30.

## Suggested branch and commit

- Branch: `agent/task-26-research-experience`
- Commit: `feat: deliver research API and frontend`
