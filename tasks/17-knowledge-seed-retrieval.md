# Task 17 — Knowledge Seed and Retrieval

## Objective

Populate the planned development dataset and implement deterministic/semantic retrieval through `KnowledgePort`.

## Dependencies

- Task 16 complete.
- Task 14 embedding abstraction available.

## Required reading

- **[`docs/adr/010-tkb-data-sourcing-embeddings.md`](../docs/adr/010-tkb-data-sourcing-embeddings.md) — Accepted, and higher authority than this brief. Read it before the stub-first policy below.**
- `plans/TRAVEL-KNOWLEDGE-CATALOG.md`
- `plans/USE-CASES.md` UC-K01–K14
- `plans/BACKLOG.md` S4-4b, S4-4d
- Stub-first policy in `plans/superpower/PLAN.md` §4.0.7 — **narrowed by ADR 010 §3 for the Knowledge port specifically** (see below)

## Scope

### Stub policy — the exception to §4.0.7

> **§4.0.7's stub-first default does not apply to the Knowledge port.** A stubbed knowledge
> adapter emits invented guides, POIs, and prices carrying **fabricated `source_refs[]`** — the
> exact failure §4.1.0 exists to prevent. Stub-first remains correct for flights, hotels, and
> payments; it inverts the product promise here.

| Environment | Rule (ADR 010 §3) |
|---|---|
| Production | Application **fails to start** if any `Stub*` knowledge adapter is wired |
| Development / CI | Permitted, but every response is flagged `sample_data: true` and the UI shows a persistent "sample data" banner |
| Provenance | Stub rows cite the reserved `source_ref` `stub:sample` — **never** a plausible-looking URL |

### Seed data

v1 coverage is **three destinations, hand-curated** (ADR 010 §1): **Tokyo (JP), Bangkok (TH),
Shanghai (CN)** — one per locale app pack. Depth beats breadth: C2/C3 quality is judged *within*
a destination.

Minimum depth per destination: 1 `destination_guide`, ≥4 `destination_area`, ≥25 `poi` (≥8 food),
≥1 `transport_mode` set, ≥3 `travel_app`, 12 months `seasonality`, and `route_segment` rows for
curated area pairs only.

Curation is a deliberate authoring task with a named owner, not a by-product of writing code.

Only these licences are permitted (ADR 010 §2):

| Source | Licence | Use |
|---|---|---|
| Wikivoyage | CC BY-SA 4.0 | Allowed — attribution + share-alike on derived text |
| OpenStreetMap / Nominatim | ODbL | Allowed — attribution |
| Official tourism boards / operators | Per-site terms | Allowed for facts with citation |
| **Google Places / Maps content** | Google ToS | **Forbidden to persist** — live request-time use only |
| Scraped aggregator reviews | — | **Forbidden** |

Provide verified development fixtures covering:

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
- **Hybrid retrieval — vector + Postgres `tsvector` fusion.** Pure vector underperforms on exactly the lexical queries this product asks ("street food", "temples"), which is why the ADR mandates fusion rather than leaving it optional.
- Retrieval parameters per ADR 010 §5: `top_k = 20`, similarity floor `0.5`, then rerank. `KnowledgeMatch` carries score and `source_ref`.
- `destination_id` filtered **pre-ANN** via the partial index from Task 16.
- `PgVectorKnowledgeAdapter` and `StubDestinationKnowledgeAdapter` sharing contract tests — the stub constrained by the stub policy above.
- Typed low-confidence/empty outcomes for unsupported data.
- `destination_not_covered` returned for a request naming an uncovered destination, with the supported list; `GET /api/v1/destinations/supported` backs the picker and the agent's honest "I don't cover that yet".

### Freshness (ADR 010 §6)

- TTL per data class: `poi.opening_hours` / `price_band` 90 days · `travel_app` 180 · `price_history` / `seasonality` 365 · `destination_guide` narrative 730.
- Expired rows stay retrievable but return `stale: true`, and consumers must downgrade confidence rather than assert. A stale opening time presented as current is the most direct way to ruin a traveller's day.

### Quality checks

- Detect duplicate IDs/names, orphan sources, invalid coordinates, stale/missing provenance, incompatible vector dimensions, and locale-app replacement conflicts.
- Add seed validation command usable in CI.

## Do not

- Do not crawl the web or add unlicensed datasets.
- Do not claim global support.
- Do not fabricate source URLs or prices.
- Do not implement C2 agent behavior.
- **Do not ship a knowledge stub that can run in production, or one whose rows cite a plausible URL.**
- Do not persist Google Places/Maps content.
- Do not generate knowledge with an LLM — that is precisely the hallucination this architecture exists to prevent.
- Do not widen coverage beyond the three curated destinations to make demos look better.
- Do not build the refresh/re-embed pipeline ([Task 40](40-tkb-refresh-reembed.md)) or admin curation ([Task 41](41-admin-knowledge-curation.md)) here.

## Validation

Run migration/seed from an empty database, adapter contract tests, semantic query fixtures such as `street food` and `temples`, and checks proving China ride-hail results prefer Didi over Uber.

## Definition of Done

- Seed dataset meets the ADR 010 §1 minimum depth for Tokyo, Bangkok, and Shanghai.
- Every result is traceable to provenance, with licence and attribution recorded.
- Structured and semantic retrieval pass shared contracts; hybrid fusion demonstrably beats pure vector on `street food` and `temples`.
- Unsupported data fails closed via `destination_not_covered`, never via a low score.
- A `Stub*` knowledge adapter cannot start in production, and dev responses carry `sample_data: true`.
- Stale rows surface `stale: true` rather than being presented as current.

## Handoff

Report coverage matrix, source and licence register, embedding model/version, retrieval API
examples, and known data limits. State which rows will need re-embedding on the first run of
[Task 40](40-tkb-refresh-reembed.md), and which entities [Task 41](41-admin-knowledge-curation.md)
must expose for correction.

## Suggested branch and commit

- Branch: `agent/task-17-knowledge-seed-retrieval`
- Commit: `feat: seed and retrieve travel knowledge`
