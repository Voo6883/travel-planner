# Task 17 — Knowledge Seed and Retrieval

## Objective

Populate the planned development dataset and implement deterministic/semantic retrieval through `KnowledgePort`.

## Dependencies

- Task 16 complete.
- Task 14 embedding abstraction available.

## Required reading

- `plans/TRAVEL-KNOWLEDGE-CATALOG.md`
- `plans/USE-CASES.md` UC-K01–K14
- `plans/BACKLOG.md` S4-4b, S4-4d
- Stub-first policy in `plans/superpower/PLAN.md` §4.0.7

## Scope

### Seed data

Provide verified development fixtures for at least the planned CN, JP, and TH coverage:

- Destination guides.
- Areas and area types.
- Sight, food, experience, nature, and nightlife POIs where applicable.
- Seasonality and price-history examples.
- Transport modes.
- Representative route segments.
- Essential/recommended local app packs, including China suppression of inactive global alternatives.
- Source/provenance records for every factual entry.

### Retrieval

- Structured SQL queries for destination/area/category/month/budget/tag filters.
- Semantic POI/destination retrieval through the pinned embedding provider and pgvector.
- Deterministic stub embedding/retrieval mode for CI.
- Hybrid ranking contract that returns source references, confidence, and freshness.
- `PgVectorKnowledgeAdapter` and realistic `StubDestinationKnowledgeAdapter` sharing contract tests.
- Typed low-confidence/empty outcomes for unsupported data.

### Quality checks

- Detect duplicate IDs/names, orphan sources, invalid coordinates, stale/missing provenance, incompatible vector dimensions, and locale-app replacement conflicts.
- Add seed validation command usable in CI.

## Do not

- Do not crawl the web or add unlicensed datasets.
- Do not claim global support.
- Do not fabricate source URLs or prices.
- Do not implement C2 agent behavior.

## Validation

Run migration/seed from an empty database, adapter contract tests, semantic query fixtures such as `street food` and `temples`, and checks proving China ride-hail results prefer Didi over Uber.

## Definition of Done

- Seed dataset supports planned C2/C3 demos for CN/JP/TH.
- Every result is traceable to provenance.
- Structured and semantic retrieval pass shared contracts.
- Unsupported data fails closed.

## Handoff

Report coverage matrix, source register, embedding model/version, retrieval API examples, and known data limits.

## Suggested branch and commit

- Branch: `agent/task-17-knowledge-seed-retrieval`
- Commit: `feat: seed and retrieve travel knowledge`
