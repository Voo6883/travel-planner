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

#### `travel_app` — locale app pack (download before you go) 🆕 P0

Each **country/destination** has apps locals use — **not** only global apps.
Grouped by **usage** (ride, maps, food, pay, trains…).

| Field | Example (China — ride-hail) |
|---|---|
| `name` | Didi |
| `local_name` | 滴滴出行 |
| `country_codes[]` | `["CN"]` |
| `category` | `ride_hail` |
| `usage_context` | "Taxis & ride-hail — Uber unavailable in mainland China" |
| `when_to_use` | Airport, cross-town, late night |
| `setup_notes` | "Chinese phone number; link 支付宝 or 微信" |
| `priority` | `essential` \| `recommended` \| `optional` |
| `replaces_global[]` | `["Uber", "Bolt"]` — suppress where inactive |
| `store_url` | App Store / 应用商店 |

**Locale examples by usage:**

| Usage | China | Japan | Thailand |
|---|---|---|---|
| Ride-hail | **滴滴出行** | GO / Japan Taxi | **Grab** |
| Maps | **高德地图** | Google Maps | Google Maps |
| Food | **大众点评** | Tabelog | Wongnai |
| Pay | **支付宝**, 微信 | PayPay, Suica | PromptPay |
| Trains | **12306** | SmartEX | — |

**Default categories surfaced:**

| Category | When | Note |
|---|---|---|
| `ride_hail` | Need a car | **Local app required** in CN, JP, SEA |
| `maps` | Navigation | May be Amap not Google in CN |
| `payment` | Pay shops / link ride apps | Alipay/WeChat in China |
| `food` | Find restaurants | Dianping, Tabelog… |
| `train_booking` | HSR / rail | 12306 in China |
| `transit` | Metro routes | City-specific |
| `translation` | Language barrier | Always useful |
| `esim` | Mobile data | Pre-trip install |

**UI — "Download before you go"** checklist on destination select / trip overview.
Chat: *"I'm going to China — what apps?"* → essential pack from KB.

See **§2.1** for full locale tables.

### 2.1 Locale app packs (by country) 🆕

When user picks a destination, surface **essential** apps to download — matched to **local usage**.

#### China (CN) — essential pack

| Usage | App | Local name | Why |
|---|---|---|---|
| Ride-hail | Didi | 滴滴出行 | Main taxi app; Uber not available |
| Maps | Amap | 高德地图 | Best navigation & transit in China |
| Payment | Alipay | 支付宝 | Pay everywhere; link to Didi |
| Payment | WeChat Pay | 微信支付 | Alternative payment |
| Food | Dianping | 大众点评 | Restaurant reviews & booking |
| Trains | 12306 | 铁路12306 | High-speed rail tickets |
| Translation | Youdao / Google | 有道翻译 | Menus & signs |

**Setup notes (China):** VPN may be needed for some global apps; install Didi + Amap + Alipay **before** landing; Chinese SIM helps for verification.

#### Japan (JP) — essential pack

| Usage | App | Local name | Why |
|---|---|---|---|
| Maps | Google Maps | — | Works well in Japan |
| Transit | Japan Transit Planner | 乗換案内 | Train routing |
| Transit card | Suica / PASMO | — | IC card for trains & shops |
| Food | Tabelog | 食べログ | Restaurant ratings |
| Ride | GO / Japan Taxi | — | Official taxi apps |

#### Thailand (TH) — essential pack

| Usage | App | Why |
|---|---|---|
| Ride-hail | Grab | Dominant ride + food delivery |
| Maps | Google Maps | Reliable |
| Food | Wongnai | Local restaurant guide |

#### LLM behavior

```
User: "I'm planning a trip to China"
  → get_travel_apps(country=CN)
  → "Before you go, download these essential apps:
     🚗 滴滴出行 (ride-hail) — like Uber but for China
     🗺️ 高德地图 (maps)
     💳 支付宝 (payment — needed for Didi & most shops)
     …"
```

**Do not** suggest Uber in China. KB `replaces_global` enforces this.

---

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
| PM-K04 | Country has **locale app pack** — essential apps per usage (ride, maps, pay…) |
| PM-K08 | China trip suggests **滴滴出行** not Uber; KB `replaces_global` enforced |
| PM-K09 | **"Download before you go"** UI shows essential pack on destination select |
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
