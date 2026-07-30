# Sample knowledge seed — file format

This directory holds the **sample** Travel Knowledge Base dataset loaded by
`com.travelplanner.infrastructure.knowledge.SampleKnowledgeSeeder` (task 17).

This file is the **contract** that [task 40](../../../../../../../../tasks/40-tkb-refresh-reembed.md)
(refresh / re-embed) and [task 41](../../../../../../../../tasks/41-admin-knowledge-curation.md)
(admin curation) write against. Change the shape here and those tasks break.

---

## 1. What this data is, and what it is not

**It is sample data, not curated facts.** Every row it produces cites the single reserved source
`stub:sample` — `licence = SAMPLE_DATA`, `trust_tier = SAMPLE`, `source_url = null` — exactly as
ADR 010 §3 requires. Nothing here was taken from Wikivoyage, OpenStreetMap, a tourism board, or any
other publisher.

Three rules were followed while writing it, and must be followed by anyone editing it:

1. **No fabricated provenance.** No invented Wikivoyage/OSM/tourism-board URL, no real-looking
   citation, no `source_ref` other than `stub:sample`. Fabricated provenance is the exact failure
   PLAN §4.1.0 and ADR 010 exist to prevent.
2. **No real venue with invented facts.** Areas, POIs, transport modes and apps all carry plainly
   generic names (`Sample Riverside Market`, `Sample Old Town`). A *real* venue name paired with a
   made-up opening time is the dangerous case, because somebody will believe it.
3. **No price anybody could mistake for sourced.** `price_history` uses the repdigit placeholders
   111 / 222 / 333 in the destination's real currency, identical in all three files. Seasonality
   bands are a fixed rotation through the enum vocabularies, also identical in all three files, so
   they cannot be read as a climate or pricing observation.

The only real facts in the dataset are the three destination rows themselves — name, ISO country
code, IANA timezone and city-centre coordinates. Those are identifiers for a place rather than
claims about it, which is why `destination` is the one catalogue table in V14–V17 with no
`source_id`. Every area/POI coordinate is a small synthetic offset from the city centre; it is not
a surveyed location.

### Depth: deliberately below the ADR's floor

ADR 010 §1 sets a **curation** target of ≥25 POIs (≥8 food), ≥4 areas, ≥3 apps, 12 months of
seasonality and curated route segments per destination. **This sample set does not satisfy that
floor and is not intended to.** It carries 10 POIs (3 food) per destination — enough to exercise
every table, every enum vocabulary and every retrieval path, and no more. The ADR's depth is a
target for *real, sourced* curation; padding sample data to hit a curation number would be twenty
more fabrications, not more coverage.

That shortfall is also why every destination is seeded `coverage_level = PARTIAL`. ADR 010 §4
admits only `FULL` destinations to C2 ranking, so sample content is stored, retrievable and clearly
labelled, but never ranked and presented as real.

---

## 2. Layout

```
knowledge/sample/
  sources.json      the source register — currently exactly one entry, the reserved stub:sample
  tokyo-jp.json     one file per destination, named for its slug
  bangkok-th.json
  shanghai-cn.json
```

The three destination slugs are **fixed**. V18 creates one partial HNSW index per slug
(`ix_poi_embedding_hnsw_tokyo`, …), so a fourth file dropped in here would seed rows that no vector
index covers. Adding a destination is a migration plus a file, in that order — the seeder's slug
list is a compile-time constant for exactly this reason.

### Everything is referenced by slug, never by UUID

`area_slug`, `transport_mode_slug`, `from_area_slug`, `to_area_slug` and `source_ref` are all
natural keys. No file contains a UUID. Ids are generated at seed time, so a re-seed against a fresh
database produces different ids and the same graph — and a file that pinned a UUID would break the
moment somebody dropped the schema.

---

## 3. `sources.json`

```jsonc
{
  "format_version": 1,
  "sources": [
    {
      "source_ref": "stub:sample",          // must be exactly this — the loader rejects anything else
      "name": "Sample data (not a real source)",
      "licence": "SAMPLE_DATA",             // KnowledgeLicence; must be SAMPLE_DATA here
      "attribution_text": "…",              // NOT NULL in V13; shown wherever sample content is displayed
      "source_url": null,                   // must be null: ADR 010 §3 forbids a plausible URL
      "retrieved_at": null,                 // null = "the seed run"; or an ISO-8601 instant
      "trust_tier": "SAMPLE"                // TrustTier; must pair with SAMPLE_DATA (V13 CHECK)
    }
  ]
}
```

`retrieved_at` is `null` throughout the sample set because there was no fetch — the data was
written, not retrieved. The loader substitutes the seed instant, so sample rows start fresh against
the ADR 010 §6 TTLs instead of being born stale. A real source file would state its actual fetch
time here; the TTL runs from that, not from the row's `created_at`.

The list exists so tasks 40/41 can add real sources without a format change. Today the loader
requires the sample entry to be present and refuses any source that is not the reserved one.

---

## 4. Destination file

Top level:

| Key | Table | Notes |
|---|---|---|
| `format_version` | — | `1`. Bump when the shape changes incompatibly. |
| `destination` | `destination` | Exactly one. |
| `guide` | `destination_guide` | Exactly one, per locale. |
| `areas[]` | `destination_area` | |
| `pois[]` | `poi` | |
| `transport_modes[]` | `transport_mode` | |
| `route_segments[]` | `route_segment` | Curated area pairs only. |
| `seasonality[]` | `seasonality` | 12 entries, months 1–12. |
| `price_history[]` | `price_history` | |
| `travel_apps[]` | `travel_app` | Country-scoped, not destination-scoped. |

Every node except `destination` carries a `source_ref`.

### `destination`

```jsonc
{
  "slug": "tokyo-jp",          // unique; must match the file name and V18's index predicates
  "name": "Tokyo",
  "country_code": "JP",        // ISO 3166-1 alpha-2, char(2)
  "timezone": "Asia/Tokyo",    // IANA zone
  "latitude": 35.6812,         // numeric(9,6); both or neither (V14 CHECK)
  "longitude": 139.7671,
  "coverage_level": "PARTIAL"  // FULL | PARTIAL | NONE — sample data is PARTIAL, see §1
}
```

### `guide`

```jsonc
{
  "locale": "en",              // `en` is authoritative for embeddings (ADR 010 §5)
  "source_ref": "stub:sample",
  "overview": "…",             // NOT NULL, must not be blank
  "food": "…",                 // nullable
  "practical": "…"             // nullable
}
```

The three fields are separate columns because ADR 010 §5 embeds them **separately**, as
`field_group` `OVERVIEW` / `FOOD` / `PRACTICAL`. Merging them into one blob would silently undo
that. A `null` section is skipped — it produces no embedding row rather than an empty one.

### `areas[]`

```jsonc
{
  "slug": "sample-old-town",   // unique within the destination
  "name": "Sample Old Town",
  "description": "…",          // nullable
  "latitude": 35.7012,         // both or neither
  "longitude": 139.7871,
  "source_ref": "stub:sample"
}
```

### `pois[]`

```jsonc
{
  "slug": "sample-riverside-market",   // unique within the destination
  "name": "Sample Riverside Market",
  "description": "…",                  // nullable
  "category": "FOOD",                  // PoiCategory: FOOD SIGHT MUSEUM NATURE SHOPPING NIGHTLIFE
                                       //              EXPERIENCE TRANSPORT_HUB
  "area_slug": "sample-riverside",     // nullable — not every POI sits in a curated area
  "tags": ["street food", "market"],   // text[]; embedded with the name and description
  "locale": "en",
  "latitude": 35.6602,
  "longitude": 139.7791,
  "opening_hours": "…",                // nullable free text; never a structured schedule
  "price_band": "BUDGET",              // nullable PriceBand: FREE BUDGET MODERATE EXPENSIVE LUXURY
  "source_ref": "stub:sample"
}
```

`name`, `description` and `tags` are embedded together as **one** chunk (ADR 010 §5) — a POI is too
short for splitting to produce fragments with standalone meaning.

### `transport_modes[]`

```jsonc
{
  "slug": "sample-metro",
  "name": "Sample Metro",
  "kind": "METRO",             // TransportKind: WALK METRO TRAIN BUS TRAM FERRY TAXI RIDESHARE BIKE CAR
  "description": "…",          // nullable
  "cost_band": "BUDGET",       // nullable PriceBand
  "tourist_friendly": true,
  "source_ref": "stub:sample"
}
```

### `route_segments[]`

```jsonc
{
  "from_area_slug": "sample-old-town",       // must name an area in this file
  "to_area_slug": "sample-station-district", // must differ from `from_area_slug` (V16 CHECK)
  "transport_mode_slug": "sample-metro",     // must name a transport mode in this file
  "duration_minutes": 9,                     // > 0
  "estimated": false,                        // true = inferred from mode heuristics, not curated
  "notes": "…",                              // nullable
  "source_ref": "stub:sample"
}
```

Curated **area pairs only** (ADR 010 Consequences). A leg nobody wrote is served as an explicit
estimate, never invented by the model. One row per `(from, to, mode)`.

### `seasonality[]`

```jsonc
{
  "month": 1,                     // 1–12; all twelve must be present
  "weather_band": "COLD",         // WeatherBand: COLD COOL MILD WARM HOT WET STORMY
  "crowd_band": "LOW",            // CrowdBand: LOW MODERATE HIGH PEAK
  "price_band": "BUDGET",         // PriceBand
  "notes": "…",                   // nullable
  "source_ref": "stub:sample"
}
```

### `price_history[]`

```jsonc
{
  "category": "HOTEL_NIGHT",      // free text; e.g. HOTEL_NIGHT MEAL_MID_RANGE TRANSIT_DAY_PASS
  "amount": 111.0,                // > 0, and scaled to the CURRENCY — see below, this one bites
  "currency": "JPY",              // ISO 4217, uppercase
  "observed_on": "2026-01-01",    // must be the FIRST of the month (V17 CHECK)
  "source_ref": "stub:sample"
}
```

The only place in the TKB holding actual money. One observation per
`(destination, category, month)` — a second is a correction, not a second truth.

**`amount` is scaled to the currency, not to the column.** The column is `numeric(12,2)` for every
currency, but `Money` enforces the currency's own minor units: JPY has none, so `4000.10 JPY` is not a
yen amount and is refused. `4000` is fine, and `4000.10 USD` is fine. Refusing beats truncating — a
price that quietly disagrees with its source is the invented fact PLAN §4.1.0 forbids — but the column
cannot express the rule, so nothing stops you writing it. `npm run seed:validate` does.

This was **F-44**: a yen price with fractional digits inserted cleanly and then threw on *every read*
of Tokyo's price history.

### `travel_apps[]`

```jsonc
{
  "country_code": "JP",           // must equal the destination's country code
  "slug": "sample-ride-hailing",  // unique within the country
  "name": "Sample Ride-Hailing App",
  "category": "RIDEHAILING",      // TravelAppCategory: RIDEHAILING TRANSIT PAYMENT FOOD_DELIVERY
                                  //                    TRANSLATION NAVIGATION ESIM
  "description": "…",             // nullable
  "ios_url": "https://example.invalid/sample-app/sample-ride-hailing/ios",
  "android_url": "https://example.invalid/sample-app/sample-ride-hailing/android",
  "source_ref": "stub:sample"
}
```

V16 requires **at least one** store link (`ck_travel_app_has_a_store_link`) — an app pack entry with
neither is not actionable. Sample entries therefore use `example.invalid`, which RFC 2606 reserves
and which can never resolve. That is the same reasoning as `admin@travelplanner.local` in
`DevAdminSeeder`: satisfy the constraint with something that is structurally incapable of being
mistaken for real.

Apps are scoped by **country**, not by destination, so the three files carry three disjoint packs.
A country appearing in two destination files would need identical app rows in both.

---

## 5. Validating a file you just edited

```bash
npm run seed:validate           # all three files, seconds, no database
npm run seed:validate -- --help # every rule, and why it exists
```

**What it checks is the domain, not a checklist.** Each node is used to construct the record it becomes
— `Money`, `Destination`, `Poi`, `TravelAppReplacement` — so the rules are whatever those records
enforce, and this document cannot fall out of step with them. Plus the cross-node facts no single
record can see: an `area_slug` naming an area the file never defines, a duplicate slug, seasonality that
is not twelve distinct months.

**Why bother, when the seeder would fail anyway.** Some of these rules are enforced on *read*, not on
write, so a bad file loads green and breaks later — see the `price_history` note above. Others are
enforced by a constraint, which fails two hundred rows in and names a table rather than your file. The
validator reports every problem in one pass, each naming the file, the node and the offending value.

The same check runs inside `SampleKnowledgeReader.readDestination()`, so it also runs during a real
seed and in `./gradlew test`. There is no way to load a file without it.

---

## 6. Running the seeder

Two gates, both of which must be open:

```yaml
spring:
  profiles:
    active: local          # `dev`, `docker` or `local` — an allow-list, not a deny-list
travelplanner:
  knowledge:
    sample-seed:
      enabled: true        # defaults to false; a developer must opt in
```

Under `prod` the seeder bean is not instantiated at all, so there is no code path to disarm.

Re-running is safe. Rows are looked up by their natural key (slug, `(destination, month)`,
`(country, slug)`, …) and existing rows are **left alone** — the seeder creates, it never updates.
A guide you corrected by hand survives the next restart, which is also what task 41's curation UI
will depend on.

Embeddings are the one thing re-checked on every run: the SHA-256 `content_hash` of the exact
embedded text is compared against the stored row, and only a mismatch re-embeds (ADR 010 §5's
re-embed trigger). Deleting the embedding tables and re-seeding therefore rebuilds them.

---

## 7. Notes for tasks 40 and 41

- **Every sample row needs re-embedding on the first task-40 run against a real provider.** Sample
  vectors are written by whichever `EmbeddingPort` bean is active, which in a default checkout is
  `StubEmbeddingAdapter`. The `embedding_model` column is nevertheless pinned to
  `text-embedding-3-small` because V18's CHECK constraints and partial indexes require that exact
  string. Stub vectors are **not** comparable with real ones — a second reason sample destinations
  stay `PARTIAL` and are never ranked.
- **`content_hash` is over the exact embedded text**, which is `overview` / `food` / `practical`
  verbatim for a guide, and `name + "\n" + description + "\n" + tags joined by ", "` for a POI.
  Task 40 must reproduce that concatenation exactly or it will re-embed the entire corpus on its
  first run.
- **Task 41 must expose for correction**: `destination_guide` (three field groups, and the
  `version` counter), `poi` (notably `opening_hours` and `price_band`, the 90-day TTL pair), and
  `travel_app` (store URLs, the 180-day TTL). Editing any of the first two must invalidate the
  matching embedding rows.

## 8. Known gaps in this dataset

- **App-pack suppression is not representable.** Task 17 asks for "China suppression of inactive
  global alternatives", but V16's `travel_app` has no column expressing that one app replaces or
  supersedes another in a market. The sample packs are therefore four parallel apps per country
  with no relationship between them. Representing suppression needs a schema change.
- **No `NAVIGATION`, `FOOD_DELIVERY` or `ESIM` app**, and no `TRAIN`, `TRAM`, `RIDESHARE`, `BIKE`
  or `CAR` transport mode. The enum values are valid; the sample set simply does not populate them.
- **`locale` is `en` only.** ADR 010 §5 makes `en` authoritative for embeddings and translates `ms`
  at presentation, so there is no second-locale row to seed.
