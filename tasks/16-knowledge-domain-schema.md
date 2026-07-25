# Task 16 — Knowledge Domain and Database Schema

## Objective

Implement the Travel Knowledge Base domain, provenance model, migrations, and persistence ports that support grounded C2/C3/C5 behavior.

## Dependencies

- Tasks 07, 14, and 15 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.1.0 and §4.1.2
- **[`docs/adr/010-tkb-data-sourcing-embeddings.md`](../docs/adr/010-tkb-data-sourcing-embeddings.md) — Accepted, and higher authority than this brief. It postdates this file; where they differ, the ADR wins.**
- `plans/TRAVEL-KNOWLEDGE-CATALOG.md`
- `plans/USE-CASES.md` UC-K01–K14
- `plans/BACKLOG.md` S1-11, S4-4b, S5-1 where shared schema is concerned

## Scope

### Domain and ports

- `KnowledgePort` and typed queries/results for guides, areas, POIs, semantic matches, seasonality, price trends, transport modes, routes, and travel apps.
- Domain types for destination, guide, area, POI, transport mode, route segment, travel app, seasonality, price history, and knowledge provenance.
- Explicit confidence/freshness/source handling and typed no-result behavior.

### Coverage model (ADR 010 §4)

- `destination.coverage_level` — `FULL` | `PARTIAL` | `NONE`.
- Only `FULL` destinations are eligible for C2 ranking; the domain must express this, not leave it to a query filter.
- A typed `destination_not_covered` outcome. An uncovered destination is **never** silently ranked low — that is indistinguishable from a genuinely poor match.

### Licensing and provenance (ADR 010 §2)

- `knowledge_source` records `licence`, `attribution_text`, `source_url`, `retrieved_at`, `trust_tier`.
- Share-alike terms propagate into derived guide text, so attribution is a product constraint surfaced in the UI — not a legal footnote.
- Freshness fields sufficient to support the per-data-class TTLs in ADR 010 §6 and a `stale` flag on read.

### Database

- Flyway migrations for the catalog entities and pgvector extension/index foundations.
- `knowledge_source` relationship and fields sufficient to identify source, refresh time, trust tier, and record provenance.
- Appropriate uniqueness, foreign keys, geographic fields, numeric money, timestamps, tags, and indexes.
- Persistence adapters mapping database records to pure domain models.

### Embedding lifecycle (ADR 010 §5)

- Every embedding table carries `embedding_model`, `embedding_dimension`, `embedding_version`, `content_hash`, `embedded_at`.
- Model pinned to `text-embedding-3-small` / 1536 dimensions; the schema must make an incompatible-dimension write impossible.
- **HNSW** index using `vector_cosine_ops`, with a partial index enabling `destination_id` filtering **before** the ANN search — post-filtering destroys recall.
- Chunking is per field group, not per row: guide `overview`, `food`, `practical` embed separately; POI embeds `name + description + tags`.
- `locale` column on `destination_guide` and `poi`; `en` is authoritative for v1 embeddings.
- Schema must support the additive model-migration path (new column + backfill, old index still serving) — never an in-place rebuild.

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
- Do not use an IVFFlat index — ADR 010 rejects it in favour of HNSW.
- Do not embed whole rows; `destination_guide` is heterogeneous and produces unusable centroids.
- Do not allow a schema in which two embedding models can share one index.

## Definition of Done

- Knowledge schema and ports cover UC-K01–K14 data needs.
- Every factual record can reference provenance, licence, attribution, and freshness.
- `coverage_level` exists and a typed `destination_not_covered` outcome is expressible.
- Vector metadata prevents incompatible embeddings; a dimension mismatch is rejected by a constraint, not by convention.
- HNSW index present with pre-ANN `destination_id` filtering.
- Migrations and adapters are tested.

## Handoff

Document schema diagram, migration numbers, query contracts, vector metadata, licence/attribution
fields, `coverage_level` semantics, and the seed format required by Task 17. State explicitly
which columns [Task 40](40-tkb-refresh-reembed.md) will write during re-embedding and which
[Task 41](41-admin-knowledge-curation.md) will expose for curation.

## Suggested branch and commit

- Branch: `agent/task-16-knowledge-schema`
- Commit: `feat: add travel knowledge domain and schema`
