# Travel Knowledge Base — schema and query contracts

> Handoff for [Task 16](../tasks/16-knowledge-domain-schema.md). Authority: [ADR 010](adr/010-tkb-data-sourcing-embeddings.md)
> — where this document and the ADR disagree, the ADR wins.
>
> Read this before writing anything that touches the TKB: [Task 17](../tasks/17-knowledge-seed-retrieval.md)
> (seed + retrieval), [Task 40](../tasks/40-tkb-refresh-reembed.md) (refresh) and
> [Task 41](../tasks/41-admin-knowledge-curation.md) (curation) all write columns defined here.

## 1. Migrations

| Migration | Tables |
|---|---|
| `V13` | `knowledge_source` |
| `V14` | `destination`, `destination_guide`, `destination_area` |
| `V15` | `poi` |
| `V16` | `transport_mode`, `route_segment`, `travel_app` |
| `V17` | `seasonality`, `price_history` |
| `V18` | `destination_guide_embedding`, `poi_embedding` + HNSW indexes |

**Next free migration: `V19`.**

## 2. Shape

```
knowledge_source ──────────────┐  (every factual row cites one; source_id is NOT NULL)
                               │
destination ──┬── destination_guide ── destination_guide_embedding  (3 rows: OVERVIEW/FOOD/PRACTICAL)
              ├── destination_area ──┐
              ├── poi ───────────────┼── poi_embedding              (1 row per POI)
              ├── transport_mode ────┤
              ├── route_segment ─────┘  (from_area → to_area, one mode)
              ├── seasonality           (12 rows, one per month)
              └── price_history

travel_app     (keyed by country_code, NOT destination — Grab covers Thailand, not just Bangkok)
```

`destination` is the only catalogue table with **no** `source_id`: it is a place, not a claim about one.

## 3. `coverage_level` semantics

`FULL` | `PARTIAL` | `NONE`, defaulting to `NONE`.

**Only `FULL` enters C2 ranking.** The rule lives in the domain — `Destination.isRankingEligible()` and
`Destination.requireRankable(supportedSlugs)` — not in a `WHERE` clause, because a filter is one omission
away from silently ranking a half-curated city.

A request naming a non-`FULL` destination raises `DestinationNotCoveredException` → **404
`destination_not_covered`**, with `details.requested` and `details.supported`. The supported list travels
with the refusal because the honest answer is never just "no".

Why this exists at all: `fitScore` sums interest match, seasonality, price fit and area coverage, so an
unseeded destination scores near zero on three of four terms and lands at the bottom — which reads as
"considered and rejected" when the truth is "never looked at".

## 4. Provenance, licence and attribution

`knowledge_source`: `source_ref` (stable handle), `name`, `licence`, `attribution_text` (NOT NULL),
`source_url` (null only for sample), `retrieved_at`, `trust_tier`.

| Licence | Attribution | Share-alike | Persistable |
|---|---|---|---|
| `CC_BY_SA_4_0` (Wikivoyage) | yes | **yes** | yes |
| `ODBL` (OSM/Nominatim) | yes | no | yes |
| `OPERATOR_TERMS` | yes | no | yes |
| `SAMPLE_DATA` | yes | no | yes |
| `PROPRIETARY_FORBIDDEN` (Google Places) | — | — | **no** |

Enforced in two places on purpose: `KnowledgeProvenance`'s constructor and
`ck_knowledge_source_not_forbidden`. The database check is what makes "forbidden to persist" survive
somebody writing a new adapter.

`SAMPLE_DATA` and `TrustTier.SAMPLE` imply each other (`ck_knowledge_source_sample_alignment`), so a stub
row cannot masquerade as curated content.

**Share-alike is a product constraint.** CC BY-SA propagates into *derived* guide text, so any page
rendering a guide must display its attribution — not a legal footnote, a UI requirement.

### Freshness

`retrieved_at` is denormalised onto **every** factual row and is that row's own fetch time — **not** the
source's. TTLs (`KnowledgeDataClass`) measure from it:

| Data class | TTL | On expiry |
|---|---|---|
| `POI_DETAILS` (`opening_hours`, `price_band`) | 90 d | flag stale |
| `TRAVEL_APP` | 180 d | flag stale |
| `SEASONAL_PRICING` | 365 d | refresh job |
| `GUIDE_NARRATIVE` | 730 d | review |

Staleness is **computed**, never stored: `KnowledgeProvenance.isStaleAt(now, dataClass)`. A stored boolean
is wrong the moment after it is written. Stale rows stay retrievable and return `stale: true`; consumers
downgrade confidence rather than assert. Exactly at TTL is **not yet** stale.

## 5. Vector metadata

Both embedding tables carry: `embedding vector(1536)`, `embedding_model`, `embedding_dimension`,
`embedding_version`, `content_hash`, `embedded_at`, plus `destination_id` and `destination_slug`.

Pinned by ADR 010 §5: **`text-embedding-3-small` / 1536**.

Three guards, each because the failure is otherwise silent:

| Guard | Mechanism |
|---|---|
| Wrong-length vector | `vector(1536)` type — `ERROR: expected 1536 dimensions, not 3` |
| Misdeclared dimension | `ck_*_embedding_dimension` |
| Second model in one index | `ck_*_embedding_model` + every HNSW index is **partial** on the model name |

**Chunking is per field group.** A guide produces three rows (`OVERVIEW`, `FOOD`, `PRACTICAL`); a POI
produces one from `name + description + tags`. Embedding a whole guide row yields an unusable centroid.

**Pre-ANN filtering.** V18 creates one partial HNSW index per destination:

```sql
WHERE destination_slug = '<slug>' AND embedding_model = 'text-embedding-3-small'
```

Postgres only uses a partial index when it can prove the query predicate implies the index predicate — so
`KnowledgeVectorSearch` **inlines the slug as a literal rather than binding it**. `destination_slug = $1`
does not imply `destination_slug = 'tokyo-jp'` under a generic plan, and binding would disable the index
with no error. A strict `^[a-z0-9-]{1,120}$` pattern makes the interpolation safe.

> **Adding a fourth destination requires a migration** — a partial-index predicate must be a constant.
> ADR 010 anticipates this: *"Adding a destination is a documented, repeatable authoring procedure."*

> **Unverified until seeding.** On empty tables `EXPLAIN` reports a sequential scan whatever the query
> says. First check after Task 17 seeds: `EXPLAIN ANALYZE` must name `ix_poi_embedding_hnsw_<destination>`.

**Model migration is additive** — new column + backfill, old index still serving. Never an in-place
rebuild; the `UNIQUE` keys include `embedding_model` so both models can coexist while the new index builds.

## 6. Query contracts — `KnowledgePort`

Read-only, and the **only** repository port not scoped by `userId`: the TKB describes the world, not
anyone's trip. No mutators — Task 40 owns re-embedding writes, Task 41 owns curation writes.

| Method | Returns |
|---|---|
| `findDestinationBySlug` / `findDestinationById` | `Optional<Destination>` — any coverage level |
| `findSupportedDestinations()` | `FULL` only, stable order |
| `findGuide(destinationId, locale)` | `Optional<DestinationGuide>` |
| `findAreas` / `findTransportModes` / `findRouteSegments` | `List<…>` |
| `findPois(destinationId, Optional<PoiCategory>)` | all, or one category |
| `findTravelApps(countryCode)` | per country |
| `findSeasonality(destinationId)` | 12 rows, Jan→Dec |
| `findPriceHistory(destinationId, category)` | newest first |
| `search(KnowledgeQuery)` | `List<KnowledgeMatch>`, most similar first, floor already applied |

**Absence is typed.** A missing guide is `Optional.empty()`; an uncovered destination is
`destination_not_covered`. Those are different facts — "no narrative for this locale" versus "we have never
looked at this city" — and collapsing them into an empty list is what ADR 010 §4 exists to prevent.

`KnowledgeQuery.destinationId` is **non-null by design**: it makes the unscoped query — the one that
silently destroys HNSW recall — impossible to express. Defaults are `topK = 20`, `similarityFloor = 0.5`.
`score` is cosine **similarity** (`1 - distance`), so higher is better.

Every returned model carries `KnowledgeProvenance`, so a caller displaying a fact always holds the citation
it must display and the age it must reason about.

## 7. Seed format required by Task 17

Reference everything by **slug**, never by generated UUID, so a re-seed does not depend on previous ids.
Per destination: 1 guide (`overview` / `food` / `practical`, `locale = en`), areas, POIs, transport modes,
route segments (by area slug pair + mode slug), 12 seasonality rows, price history, plus country-scoped
travel apps and a `sources` file.

ADR 010 §1 curated depth per destination: 1 guide, ≥4 areas, ≥25 POIs (≥8 `FOOD`), ≥1 transport mode set,
≥3 travel apps, 12 months seasonality, route segments for curated pairs only.

Constraints a seed will hit:
- `price_history.observed_on` must be the **first of a month**; `amount > 0`; `currency` uppercase ISO 4217.
- `seasonality` is unique per `(destination, month)`; all 12 required for `FULL`.
- `route_segment` forbids `from_area = to_area`; `duration_minutes > 0` and **whole minutes** (the column is
  `integer`, so `RouteSegment` rejects a sub-minute `Duration` rather than truncating silently).
- `travel_app` needs at least one store link.
- `poi.slug` unique per destination; `destination_area.slug` unique per destination.

Sample data must use the reserved `stub:sample` source with `SAMPLE_DATA` / `SAMPLE`, and must **not** be
marked `FULL` — that would let unverified content be ranked and presented as real.

## 8. Who writes what

**Task 40 (refresh / re-embed)** writes, on the embedding tables: `embedding`, `embedding_model`,
`embedding_dimension`, `embedding_version`, `content_hash`, `embedded_at`; and on the catalogue tables:
`retrieved_at` plus the refreshed factual columns. The re-embed trigger is a `content_hash` mismatch —
unchanged rows are never re-embedded, which is what stops a nightly refresh re-embedding the whole corpus.

**Task 41 (curation)** exposes for editing: `destination_guide` (`overview`, `food`, `practical`), `poi`
(`name`, `description`, `category`, `tags`, `opening_hours`, `price_band`, coordinates, `area_id`), and
`travel_app` (`name`, `category`, `description`, store URLs) — each with its `source_id` and an audit
trail. `destination_guide` and `poi` carry a `version` column for that UI's optimistic locking; note it is
**not** ADR 008's user-versus-agent lock and the records deliberately do not implement `Versioned`.

Neither task may change `embedding_model` or `embedding_dimension` in place — see §5.

## 9. Known gaps

Recorded in [`tasks/STATUS.md`](../tasks/STATUS.md): **F-27** (`DestinationArea` coordinates unchecked —
record and V14 must move together), **F-28** (`KnowledgeQuery` array-based equality), **F-29**
(`Destination.timezone` never validated as an IANA zone, which Task 28 depends on), **F-30**
(`DestinationNotCoveredException.supportedSlugs` is `transient`).
