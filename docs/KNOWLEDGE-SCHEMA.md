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
| `V19` | `planner_session`, `conversation`, `message` (task 20 — not TKB, listed for numbering) |
| `V20` | `trip_brief` brief columns (task 18 — not TKB, listed for numbering) |
| `V21` | `travel_app_replacement` |
| `V22` | rebuilds `ix_poi_fulltext` with the `english` text-search configuration |

**Next free migration: run `npm run code-map` — it derives the number from the filenames.** A figure
written here goes stale the moment two branches read it on different days, which is exactly how two
migrations end up sharing a version and Flyway refuses to start the second one.

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

travel_app ────┬── travel_app_replacement
               │      (keyed by country_code, NOT destination — Grab covers Thailand, not just
               │       Bangkok. The replacement row says "the app you already have does not work
               └───    here"; its replaced_app_key is a GLOBAL slug and deliberately not an FK)
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

**Model migration is additive** — new column + backfill, old index still serving. Never an in-place
rebuild; the `UNIQUE` keys include `embedding_model` so both models can coexist while the new index builds.

### 5.2 Hybrid retrieval — how the two arms are fused (gate 17B)

ADR 010 §5 mandates fusion rather than offering it, and gives the reason: the plan's own worked examples,
**street food** and **temples**, are lexical. An embedding places "street food" near *food street*, *night
market* and *casual dining*, so a row that says the words competes with everything that means roughly the
same. Cosine similarity has no way to express "this row literally contains what you asked for".

| Arm | Class | Reads | Index |
|---|---|---|---|
| Vector | `KnowledgeVectorSearch` | `poi_embedding`, `destination_guide_embedding` | partial HNSW per destination (V18) |
| Lexical | `KnowledgeFullTextSearch` | `poi` | `ix_poi_fulltext`, GIN (V15, rebuilt by V22) |

`KnowledgeHybridSearch` runs both and fuses them; `KnowledgeRepositoryAdapter.search` calls it.

**The lexical arm is POI-only, deliberately.** Lexical retrieval wins on short name-like text where an
embedding has too few tokens to disambiguate. A guide is long-form narrative — the case embeddings are
good at, and where a keyword hit says little, since "street food" appears in every guide's food section.
There is a structural reason too: guides are embedded per `field_group`, so a vector match is per section
while a lexical match would be per row, and fusing them would collide two snippets on one key.

**Fusion is on rank, not on score.** Cosine similarity is bounded and roughly calibrated across queries;
`ts_rank` is unbounded and is not. A weighted sum needs a per-query normaliser, and every normaliser
derived from the returned rows (divide-by-max, min-max, z-score) makes a document's score depend on which
*other* documents came back — adding one irrelevant row reorders rows that did not move. Reciprocal rank
fusion discards both scores and keeps only each arm's ordering:

```
fused(d) = Σ over arms  1 / (K + rank_arm(d))        K = 60, rank is 1-based
```

Normalised by the best a document could score — first in every arm *consulted*, not every arm that
answered — which makes `relevance` readable:

| Where the document ranked | `relevance` |
|---|---|
| 1st in both arms | 1.00 |
| 10th in both arms | 0.87 |
| 1st in one arm, absent from the other | 0.50 |
| 20th in one arm only | 0.38 |

Ties break toward the **vector arm**, because ranking is built on semantic retrieval and the lexical arm is
the corrective.

**The similarity floor stays inside the vector arm.** It is a cosine floor. Applied to a fused relevance it
would drop every single-arm match — and a single-arm match is the only kind fusion can add over pure
vector, so the filter would silently undo the fusion. `KnowledgeMatch.relevance` was renamed from `score`
for exactly this reason.

**V22: `english`, not `simple`.** V15 built `ix_poi_fulltext` with the `simple` configuration, which does
no stemming — so `websearch_to_tsquery('simple','temples')` does not match a POI named "Sample Old Town
Temple", and `markets` does not match "Market". One of the ADR's two named example queries did not work.
It was invisible because the sample seed's temple POI happens to carry the plural in its description as
well as the singular in its name, so a test written against seeded data passes either way; only a fixture
that deliberately omits the plural distinguishes the two. On non-English content `english` passes unknown
tokens through unchanged, so there is nothing to lose, and ADR 010 §5 makes `en` the authoritative locale
for embedded text anyway.

**Measured claim (task 17 DoD).** `KnowledgeHybridSearchIT` constructs a POI whose text answers the query
and whose embedding sits at 0.30 — below the 0.50 floor, so pure vector does not return it *at all*. Fusion
does. What fusion buys there is **recall, not ranking**: the two rows come back tied at 0.50, each being
first in one arm and absent from the other.

### Are the partial HNSW indexes actually used? (F-32, measured 2026-07-30)

V18 builds one HNSW index per covered destination, predicated on
`destination_slug = '…' AND embedding_model = '…'`. Whether the planner ever chooses one was waived by
task 16 and recorded as **F-32**. Measured on PostgreSQL 16.6 + pgvector 0.8.1, against a schema
migrated from empty, using the production query shape:

| `poi_embedding` rows | heap pages | plan |
|---|---|---|
| 100 | 2 | `Seq Scan` |
| 600 | ~30 | `Seq Scan` |
| 2,000 | 25–53 | `Index Scan using ix_poi_embedding_hnsw_tokyo` |
| 20,000 | 247 | `Index Scan using ix_poi_embedding_hnsw_tokyo` |

**The mechanism works. It is not currently engaged.** §1's curation floor is ≥25 POIs per destination —
about 75 embeddings across the three covered cities, two orders of magnitude below the crossover. A
1536-dimension vector is TOASTed, so a small embedding table is a couple of heap pages and reading all
of it genuinely beats descending a graph; the planner is right to refuse the index today. The six
indexes are insurance for a corpus that does not exist yet, and they cost write and build time now.
That is the number to weigh if anyone proposes dropping them until 17C lands real curation.

**Two things this did not establish.** First, that binding the slug *always* loses the index: the same
query at the same row count was observed choosing both plans in different databases, because it turns
on custom-versus-generic plan selection and on statistics. The literal interpolation stays — it is the
shape reliably observed to reach the index — but the converse is not a claim this repository makes.
Second, anything about a plan as a *test*: `KnowledgeVectorSearchContractTest` asserts the query
**shape** these measurements were taken against, and deliberately runs no `EXPLAIN`. A plan is a
property of the SQL plus the whole database's state, and in a suite sharing one database that is not
controllable without dictating the answer.

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
| `search(KnowledgeQuery)` | `List<KnowledgeMatch>`, most relevant first — **hybrid**, see §5.2 |

**Absence is typed.** A missing guide is `Optional.empty()`; an uncovered destination is
`destination_not_covered`. Those are different facts — "no narrative for this locale" versus "we have never
looked at this city" — and collapsing them into an empty list is what ADR 010 §4 exists to prevent.

`KnowledgeQuery.destinationId` is **non-null by design**: it makes the unscoped query — the one that
silently destroys HNSW recall — impossible to express. Defaults are `topK = 20`, `similarityFloor = 0.5`.

`KnowledgeMatch.relevance` is **not** a cosine similarity — it is a fused rank, and the field was renamed
from `score` when fusion landed so that the change of meaning could not pass unnoticed. Do not compare it
with `similarityFloor`: that floor is a cosine floor applied inside the vector arm, and applying it to a
fused relevance drops every single-arm match, which is the only kind fusion can add. See §5.2.

Every returned model carries `KnowledgeProvenance`, so a caller displaying a fact always holds the citation
it must display and the age it must reason about.

## 7. Seed format required by Task 17

Reference everything by **slug**, never by generated UUID, so a re-seed does not depend on previous ids.
Per destination: 1 guide (`overview` / `food` / `practical`, `locale = en`), areas, POIs, transport modes,
route segments (by area slug pair + mode slug), 12 seasonality rows, price history, plus country-scoped
travel apps and a `sources` file.

A travel app may carry a `replaces` array — the V21 suppressions, nested under the local app rather than
declared at the top level. Nested is where a curator can get it right: the statement is "this app replaces
that one", and a top-level list would repeat the local app's slug, which is one more thing to typo into a
suppression that matches nothing.

```json
{
  "country_code": "CN",
  "slug": "sample-ride-hailing",
  "category": "RIDEHAILING",
  "replaces": [
    {
      "replaced_app_key": "uber",
      "replaced_app_name": "Uber",
      "reason": "NOT_AVAILABLE",
      "detail": "The sentence the traveller reads. Market-specific, and writing it is the point at which somebody checks whether it is still true."
    }
  ]
}
```

`reason` mirrors `domain/enums/AppReplacementReason`: `NOT_AVAILABLE`, `NETWORK_BLOCKED`,
`NEEDS_LOCAL_PAYMENT`, `NOT_THE_LOCAL_STANDARD`. Four categories rather than free text because the UI
wording differs — only the last one is advice rather than a warning, and rendering advice in a red box
trains travellers to ignore the box. `MigrationContractTest` asserts the enum and the CHECK constraint
agree, so adding a reason without widening the constraint fails the build.

ADR 010 §1 curated depth per destination: 1 guide, ≥4 areas, ≥25 POIs (≥8 `FOOD`), ≥1 transport mode set,
≥3 travel apps, 12 months seasonality, route segments for curated pairs only.

Constraints a seed will hit:
- `price_history.observed_on` must be the **first of a month**; `amount > 0`; `currency` uppercase ISO 4217.
- `seasonality` is unique per `(destination, month)`; all 12 required for `FULL`.
- `route_segment` forbids `from_area = to_area`; `duration_minutes > 0` and **whole minutes** (the column is
  `integer`, so `RouteSegment` rejects a sub-minute `Duration` rather than truncating silently).
- `travel_app` needs at least one store link.
- `travel_app_replacement.replaced_app_key` must be a lower-case slug (`uber`, `google-maps`). Enforced
  in SQL, in the record, **and** at load time, because the failure is silent: a key with a trailing
  space or a capital letter matches no app, so the pack renders and the warning is simply absent.
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
`travel_app` (`name`, `category`, `description`, store URLs) plus its `travel_app_replacement` rows —
each with its `source_id` and an audit trail. Replacements need the same review discipline as any other
claim: "Uber does not work here" stops being true the day it starts working, and a stale suppression is
worse than a stale store link because it actively steers a traveller away from an app that now works. `destination_guide` and `poi` carry a `version` column for that UI's optimistic locking; note it is
**not** ADR 008's user-versus-agent lock and the records deliberately do not implement `Versioned`.

Neither task may change `embedding_model` or `embedding_dimension` in place — see §5.

## 9. Known gaps

**Closed 2026-07-30** (the dev-branch review pass): **F-27** — `DestinationArea` now range-checks both
coordinates, matching `Destination`; **F-28** — `KnowledgeQuery` has value equality over its `float[]`,
so task 37's semantic cache can key on it instead of missing on every lookup; **F-29** —
`Destination.timezone` is validated against the JVM's tzdb, so a bad zone fails at seed time rather than
in task 28's scheduler. The review's schema gap (no way to say "Didi replaces Uber in China") is closed
by V21 and §7 above.

**Still open**, recorded in [`tasks/STATUS.md`](../tasks/STATUS.md): **F-30**
(`DestinationNotCoveredException.supportedSlugs` is `transient`), **F-34** (real curation unstarted — the
committed dataset is deliberately SAMPLE and below ADR 010 §1's floor of 25 POIs, so
`findSupportedDestinations()` is empty and nothing can be ranked; capability gate **17C**).

V21's own gap, recorded here rather than as a finding because it is a scope decision rather than a
defect: the table cannot express "this app does not work here and there is no local alternative".
`local_app_id` is `NOT NULL`, because a row's entire content is "install this one instead". Task 29
(route and mobility) is the first consumer that might need the alternative-less form; adding it is an
additive migration and a nullable column, not a redesign.
