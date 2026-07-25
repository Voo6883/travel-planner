# ADR 010: TKB data sourcing, coverage, and embedding lifecycle

## Status

Accepted

## Context

The Travel Knowledge Base is the product's differentiator. `PLAN.md` §4.1.0 makes three
non-negotiable claims: retrieve before claiming, never invent facts, provenance on every field.

The plan's entire ingestion specification is two lines: Phase 1 = "curated JSON seed per top
destination", Phase 2+ = "web crawl / licensed datasets / periodic refresh job". Unresolved:
which sources (and under which licences), how many destinations, who authors them, what happens
when a destination has no data, and how embeddings are produced, versioned, or rebuilt.

Four consequences follow directly:

- **Stub-first contradicts the KB.** §4.0.7 defaults external adapters to stubs, and a
  `StubDestinationKnowledgeAdapter` exists so "C2/C3 work without external ingest". A stubbed
  knowledge adapter emits **invented guides, POIs, and prices carrying fabricated `source_refs[]`**
  — the exact failure §4.1.0 forbids. Stub-first is correct for flights and hotels; it inverts the
  product promise for the KB.
- **Coverage gaps are silent.** `fitScore` sums interest match, seasonality, price fit, and area
  coverage. A destination with no seeded rows scores ~0 on three of four terms and simply ranks
  low — indistinguishable from a genuinely poor match.
- **Licensing propagates into API responses**, because §4.1.0 requires citing sources.
- **Embeddings have no lifecycle** — no model/dimension columns, no re-embed trigger, no rebuild path.

## Decision

### 1. v1 coverage: three destinations, hand-curated

**Tokyo (JP), Bangkok (TH), Shanghai (CN)** — matching the locale app packs already specified in
`TRAVEL-KNOWLEDGE-CATALOG.md` §2.1, giving one destination per app-pack country.

Depth per destination: 1 `destination_guide`, ≥4 `destination_area`, ≥25 `poi` (≥8 food), ≥1
`transport_mode` set, ≥3 `travel_app`, 12 months `seasonality`, and `route_segment` rows for
curated area pairs only. Curation is a deliberate authoring task with a named owner, not a
by-product of a code task.

### 2. Licensing — allowed and forbidden sources

| Source | Licence | Use |
|---|---|---|
| **Wikivoyage** | CC BY-SA 4.0 | Allowed — attribution + share-alike on derived guide text |
| **OpenStreetMap / Nominatim** | ODbL | Allowed — attribution; geometry and place data |
| **Official tourism boards / operators** | Per-site terms | Allowed for facts (hours, fares) with citation |
| **Google Places / Maps content** | Google ToS | **Forbidden to persist.** May be used live at request time only, never stored in the TKB |
| Scraped aggregator reviews | Prohibited | Forbidden |

`knowledge_source` records `licence`, `attribution_text`, `source_url`, `retrieved_at`,
`trust_tier`. Attribution is surfaced in the UI wherever derived content is displayed. Because
share-alike terms flow into derived guides, this is a product constraint, not a legal footnote.

### 3. No stub knowledge adapter in any environment that looks real

| Rule | Detail |
|---|---|
| Production | Application **fails to start** if any `Stub*` knowledge adapter is wired |
| Development | Permitted, but every response is flagged `sample_data: true` and the UI shows a persistent "sample data" banner |
| Fabricated provenance | Forbidden — stub rows cite a reserved `source_ref` of `stub:sample`, never a plausible URL |

### 4. Explicit coverage model

`destination.coverage_level`: `FULL` | `PARTIAL` | `NONE`.

- Only `FULL` destinations enter C2 ranking.
- A request naming an uncovered destination returns typed `destination_not_covered` with the
  supported list — it is **never** silently ranked low.
- `GET /api/v1/destinations/supported` backs the picker and the chat agent's honest "I don't cover
  that yet" response.

This turns §4.1.0's "empty / `low_confidence`" value into an actual flow.

### 5. Embedding lifecycle

| Item | Decision |
|---|---|
| Model | **`text-embedding-3-small`, 1536 dimensions** — pinned per index (§5.4) |
| Columns | `embedding_model`, `embedding_dimension`, `embedding_version`, `content_hash`, `embedded_at` on every embedding table |
| Chunking | Per **field group**, not whole-row — guide `overview`, `food`, `practical` embed separately; POI embeds `name + description + tags` |
| Index | **HNSW** (`vector_cosine_ops`) |
| Filtering | `destination_id` filtered **pre-ANN** via a partial index — post-filtering destroys HNSW recall |
| Retrieval | `top_k = 20`, similarity floor `0.5`, then rerank; `KnowledgeMatch` carries score + `source_ref` |
| Hybrid search | Vector + Postgres `tsvector` fusion — the plan's own examples ("street food", "temples") are lexical and underperform with pure vector |
| Re-embed trigger | `content_hash` mismatch on ingest; unchanged rows are never re-embedded |
| Model migration | Additive column + backfill, serving from the old index until the new one is complete. Never an in-place rebuild |
| Locale | `destination_guide` and `poi` gain a `locale` column; `en` is authoritative for v1 embeddings, `ms` UI strings are translated at presentation, not embedded |

### 6. Freshness by data class

| Data | TTL | On expiry |
|---|---|---|
| `poi.opening_hours`, `price_band` | 90 days | Flag stale; agent must not state hours as current |
| `travel_app` (store URLs, availability) | 180 days | Flag stale |
| `price_history`, `seasonality` | 365 days | Refresh job |
| `destination_guide` narrative | 730 days | Review |

Stale rows remain retrievable but are returned with `stale: true`, and the agent must downgrade
confidence rather than assert. A stale opening-hours presented as fact is the most direct way to
ruin a traveller's day.

### 7. Curation path

Admin CRUD over `destination_guide`, `poi`, and `travel_app` with `knowledge_source` provenance
and an audit trail. Without it, correcting a closed restaurant requires a Flyway migration and a
redeploy — an operational dead end for a knowledge product.

## Rationale

| Factor | 3 curated destinations (chosen) | Wide shallow seed | Stub KB until Phase 2 |
|---|---|---|---|
| Honours "no invented facts" | Yes | Partially | **No** |
| Demo credibility | High within coverage | Low everywhere | Fabricated |
| Authoring cost | Bounded and estimable | Unbounded | Zero |
| Exercises full schema | Yes — guides, areas, POIs, routes, apps | Rarely | No |
| Licence risk | Controlled and reviewed | Grows with breadth | Deferred, not removed |

Depth over breadth is chosen because C2/C3 quality is judged **within** a destination. Three
destinations covered well demonstrate the product; thirty covered thinly demonstrate the failure
mode.

## Consequences

- TKB curation moves onto the **critical path** for Phase 1 — it is not a Phase 2 concern. C2 and
  C3 cannot be honestly demonstrated without it.
- Tasks **16** and **17** gain: the licence register, `coverage_level`, the embedding column set,
  hybrid retrieval, and the stub-in-prod startup assertion. A new task is required for the
  ongoing refresh/re-embed pipeline and admin KB curation (neither is currently covered).
- `route_segment` is curated for area pairs only; the null-leg fallback must be explicit
  (`estimated: true` with mode heuristics) rather than LLM-invented.
- Adding a destination is a documented, repeatable authoring procedure with a data-quality
  validator in CI (`PM-K03`, `PM-K04`).
- Live Google Places usage, if introduced later, is request-time only and requires its own ADR.

## Alternatives considered

- **Stub KB through Phase 1** — rejected; fabricated provenance directly violates §4.1.0 and would
  make every C2 demo dishonest.
- **LLM-generated knowledge base** — rejected; it is precisely the hallucination the architecture
  exists to prevent.
- **Licensed commercial travel dataset** — deferred; no vendor selected, cost unknown. Revisit
  when scaling beyond hand curation.
- **Whole-row embeddings** — rejected; `destination_guide` is heterogeneous JSON and embedding it
  whole produces unusable centroids.
- **IVFFlat index** — rejected; HNSW gives better recall at this data size, and the plan left the
  choice open, which is itself a migration risk.
