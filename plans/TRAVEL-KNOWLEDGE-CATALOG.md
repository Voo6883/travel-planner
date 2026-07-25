# Travel Knowledge Base — PM Review & Catalog

> PM review of TKB scope for LLM + knowledge-based planning.
> Technical rules: [`superpower/PLAN.md`](superpower/PLAN.md) §4.1.0, §4.1.2.
> Use cases: [`USE-CASES.md`](USE-CASES.md) §G, §C2, §C3.

---

## 1. PM review summary

### What works today (v1 plan)

| Strength | Why it matters |
|---|---|
| Destination + guide + areas + POIs | Answers *where* and *what the place is like* |
| Food POIs + food guide section | Answers *what to eat* |
| Seasonality + price history | Answers *when* and *cost trend* |
| RAG over embeddings | Natural queries: "street food", "temples" |
| `source_refs` + no-hallucination rule | Trust — critical for travel |

### Gaps identified (this review)

| Gap | Traveller pain | Priority |
|---|---|---|
| **No day timeline** | "What time should I go? How long per stop?" | **P0** |
| **No route between stops** | "How do I get from A to B?" | **P0** |
| **No transport mode catalog** | "Train, bus, or taxi?" | **P0** |
| **No app recommendations** | "Which app for maps / transit card / Grab?" | **P0** |
| Multi-city route order | "Tokyo → Kyoto → Osaka — best order?" | P1 |
| Inter-city transport | "Shinkansen vs flight?" | P1 |

### PM decision

Extend TKB with a **Mobility & Timeline** layer. C3 itinerary must output:
1. **Timeline** — time blocks per item (start/end, duration)
2. **Route legs** — how to move between items (mode, duration, cost band)
3. **Transport modes** — what options exist in this destination
4. **Travel apps** — which apps to install (maps, transit, ride-hail, etc.)

LLM narrates and sequences; KB supplies route facts and app names.

---

## 2. Full knowledge catalog (enhanced)

### Layer A — Macro (where & when)

| Entity | Fields | Traveller question |
|---|---|---|
| `destination` | name, country, region, type | Where can I go? |
| `seasonality` | month, weather, crowds, peak_flag | When is best? |
| `price_history` | route, month, avg_price, trend | Is it expensive now? |

### Layer B — Place (what it's like)

| Entity | Fields | Traveller question |
|---|---|---|
| `destination_guide` | overview, best_for, avoid_if, getting_around_summary | What's it like? |
| `destination_area` | name, area_type, vibe_tags, why_visit | Where inside the city? |

### Layer C — Points of interest

| Entity | `poi.category` | Traveller question |
|---|---|---|
| `poi` | `sight` | What to see? |
| `poi` | `food` | Where to eat? |
| `poi` | `experience` | What to do? |
| `poi` | `nature` | Outdoor options? |
| `poi` | `nightlife` | Evening options? |

POI fields: `name`, `description`, `area_id`, `lat/lng`, `best_time`, `avg_visit_mins`, `opening_hours`, `price_band`, `tags[]`, `source_ref`.

### Layer D — Mobility (how to move) 🆕 P0

| Entity | Purpose | Traveller question |
|---|---|---|
| `transport_mode` | Modes available per destination | What transport exists here? |
| `route_segment` | Leg A → B with mode, time, cost | How do I get there? |
| `travel_app` | Recommended apps per destination | Which app should I use? |

#### `transport_mode` (per destination)

| Field | Example (Tokyo) |
|---|---|
| `mode` | `METRO`, `TRAIN`, `BUS`, `WALK`, `TAXI`, `RIDE_HAIL`, `FERRY`, `RENTAL_BIKE`, `DOMESTIC_FLIGHT` |
| `display_name` | "Tokyo Metro" |
| `when_to_use` | "Best for central Tokyo; avoid rush hour 7–9am" |
| `payment_hint` | "Suica / Pasmo IC card" |
| `typical_cost_band` | `low` \| `mid` \| `high` |
| `recommended_app_ids[]` | FK → `travel_app` |

#### `route_segment` (template legs in KB)

| Field | Example |
|---|---|
| `from_ref` | `poi:123` or `area:gion` |
| `to_ref` | `poi:456` or `area:arashiyama` |
| `transport_mode` | `TRAIN` |
| `duration_mins` | 25 |
| `distance_km` | 8.2 |
| `cost_band` | `{ "min": 200, "max": 400, "currency": "JPY" }` |
| `instructions` | "Take JR Sagano Line from Kyoto Station" |
| `recommended_app_ids[]` | Google Maps, Japan Transit Planner |
| `source_ref` | KB provenance |

Used by C3 to chain POIs into a **travel route**; LLM picks from KB templates, DSA optimizes order (§4.0.3).

#### `travel_app` (recommended apps) 🆕 P0

| Field | Example |
|---|---|
| `name` | Google Maps |
| `category` | `maps` \| `transit` \| `ride_hail` \| `food` \| `translation` \| `booking` \| `esim` \| `transit_card` |
| `platforms` | `["ios", "android"]` |
| `destinations[]` | country or destination scope |
| `why_recommended` | "Best turn-by-turn + transit in Japan" |
| `deep_link` | App Store / Play Store URL |
| `pairs_with_mode` | `METRO`, `WALK` — optional |
| `source_ref` | Official store link |

**Default app categories per trip (PM spec):**

| Category | When surfaced | Examples |
|---|---|---|
| `maps` | Every destination | Google Maps, Apple Maps, Citymapper |
| `transit` | Has metro/train | Japan Transit Planner, Moovit |
| `transit_card` | IC card destinations | Suica (JP), Oyster (UK) |
| `ride_hail` | Taxi alternative | Grab (SEA), Uber, Bolt |
| `translation` | Non-English primary | Google Translate |
| `food` | Food-heavy trips | Tabelog (JP), Yelp |
| `booking` | C4 phase | Airline app, hotel app |
| `esim` | International | Airalo, Holafly |

### Layer E — Timeline (when during the day) 🆕 P0

| Entity | Purpose | Traveller question |
|---|---|---|
| `itinerary_day` | Day container | What's my plan for Day 2? |
| `itinerary_item` | Scheduled block + POI | What time? How long? |
| `itinerary_leg` | Transport between items | How do I get to the next stop? |

#### `itinerary_item` (enhanced)

| Field | Example |
|---|---|
| `scheduled_start` | `09:30` |
| `scheduled_end` | `11:00` |
| `duration_mins` | 90 |
| `poi_id` | FK → `poi` |
| `item_type` | `sight` \| `food` \| `transit` \| `free_time` |
| `notes` | "Arrive early — crowds after 10am" |

#### `itinerary_leg` (per trip, instance of route)

| Field | Example |
|---|---|
| `from_item_id` | previous stop |
| `to_item_id` | next stop |
| `route_segment_id` | optional FK to KB template |
| `transport_mode` | `METRO` |
| `duration_mins` | 15 |
| `recommended_app_ids[]` | Google Maps |
| `instructions` | From KB or LLM summary of KB |

**Timeline UI:** vertical day view — time on left, POI cards, **legs** between cards showing mode icon + duration + app chip.

### Layer F — Semantic retrieval

| Entity | Purpose |
|---|---|
| `destination_embedding`, `poi_embedding` | RAG: "romantic walk", "quick lunch near temple" |
| `knowledge_source` | Provenance audit |

---

## 3. Feature mapping

| Feature | KB layers used |
|---|---|
| **C2 — choose destination** | A, B, C |
| **C2 — traveler guide card** | B, C + mobility summary from D |
| **C3 — itinerary** | C, D, E (timeline + routes + apps) |
| **C5 — chat** | All — `get_route`, `get_transport_modes`, `get_travel_apps` tools |

---

## 4. Acceptance criteria (PM sign-off)

| ID | Criterion |
|---|---|
| PM-K01 | Every itinerary day shows **timeline** (start/end per item) |
| PM-K02 | Every gap between items shows **route leg** (mode + duration) |
| PM-K03 | Destination has ≥1 **transport_mode** in KB seed |
| PM-K04 | Destination has ≥3 **travel_app** recommendations (maps + transit + 1 local) |
| PM-K05 | Route legs cite `source_ref` or `route_segment_id` — not invented |
| PM-K06 | Chat answers *"how do I get from X to Y?"* from KB |
| PM-K07 | Chat answers *"which app for metro here?"* from `travel_app` |

---

## 5. Phasing

| Phase | Deliverable |
|---|---|
| **1 (P0)** | Schema + seed: transport_mode, route_segment, travel_app; C3 timeline + legs |
| **2 (P1)** | Multi-city `route_segment` chains; inter-city mode compare |
| **3 (P2)** | Live GTFS / Maps API integration for real-time routes |
