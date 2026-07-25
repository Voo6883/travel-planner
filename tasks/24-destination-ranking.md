# Task 24 — Deterministic Destination Ranking

## Objective

Implement the pure, testable ranking and filtering logic that determines recommendation order independently of LLM prose.

## Dependencies

- Tasks 17, 18, and 23 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0.3 and C2 ranking formula
- `plans/TRAVEL-KNOWLEDGE-CATALOG.md` ranking signal
- `plans/USE-CASES.md` UC-C2-04, C2-05, C2-11
- `plans/BACKLOG.md` S4-4

## Scope

- Domain algorithm types for candidate signals, scoring weights, score breakdown, ranked destination, and exclusion reason.
- Deterministic signals for interest/POI/food match, seasonality fit, budget/price fit, area/pace/party coverage, and confidence/freshness penalties as supported by available data.
- Hard filters for impossible budget/date/support conditions before scoring.
- Stable tie-breaking and top-K behavior.
- Typed empty/no-confident-result outcome.
- No generic maps or provider/LLM dependencies.
- Table-driven unit tests for empty, single, ties, missing signals, stale data, boundaries, and country/destination fixtures.
- Document complexity and configurable/default weights; persist version/score breakdown with later recommendations.
- Benchmark or timing evidence only if dataset size warrants it.

## Do not

- Do not ask an LLM to calculate fit scores.
- Do not hide exclusions inside narrative text.
- Do not load unbounded datasets into application memory when database prefiltering is appropriate.

## Definition of Done

- Same inputs always produce the same ranking and breakdown.
- Edge cases and no-result behavior are tested.
- Algorithm is pure and framework-free.
- Later agent/UI can explain scores from structured fields.

## Handoff

Document weight configuration, inputs required from retrieval, output schema, exclusions, tie-breaks, and versioning.

## Suggested branch and commit

- Branch: `agent/task-24-destination-ranking`
- Commit: `feat: add deterministic destination ranking`
