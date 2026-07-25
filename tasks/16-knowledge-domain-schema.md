# Task 16 — Knowledge Domain and Database Schema

## Objective

Implement the Travel Knowledge Base domain, provenance model, migrations, and persistence ports that support grounded C2/C3/C5 behavior.

## Dependencies

- Tasks 07, 14, and 15 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.1.0 and §4.1.2
- `plans/TRAVEL-KNOWLEDGE-CATALOG.md`
- `plans/USE-CASES.md` UC-K01–K14
- `plans/BACKLOG.md` S1-11, S4-4b, S5-1 where shared schema is concerned

## Scope

### Domain and ports

- `KnowledgePort` and typed queries/results for guides, areas, POIs, semantic matches, seasonality, price trends, transport modes, routes, and travel apps.
- Domain types for destination, guide, area, POI, transport mode, route segment, travel app, seasonality, price history, and knowledge provenance.
- Explicit confidence/freshness/source handling and typed no-result behavior.

### Database

- Flyway migrations for the catalog entities and pgvector extension/index foundations.
- `knowledge_source` relationship and fields sufficient to identify source, refresh time, trust tier, and record provenance.
- Appropriate uniqueness, foreign keys, geographic fields, numeric money, timestamps, tags, and indexes.
- Embedding metadata that records provider/model/dimension/version.
- Persistence adapters mapping database records to pure domain models.

### Tests

- Migration from empty database.
- Constraint/index tests.
- Persistence adapter integration tests.
- User-independent read model and no accidental coupling to Trip.

## Do not

- Do not seed destination content yet; Task 17 owns it.
- Do not implement research agents or itinerary scheduling.
- Do not let LangChain4j or JPA types enter the domain.
- Do not treat LLM-generated prose as a source record.

## Definition of Done

- Knowledge schema and ports cover UC-K01–K14 data needs.
- Every factual record can reference provenance and freshness.
- Vector metadata prevents incompatible embeddings.
- Migrations and adapters are tested.

## Handoff

Document schema diagram, migration numbers, query contracts, vector metadata, and seed format required by Task 17.

## Suggested branch and commit

- Branch: `agent/task-16-knowledge-schema`
- Commit: `feat: add travel knowledge domain and schema`
