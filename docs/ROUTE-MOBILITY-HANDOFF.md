# Route and mobility handoff (Task 29 → Tasks 30, 31, 35/36)

Task 29 fills the gaps task 28 left between scheduled blocks: every A→B leg is resolved from the
knowledge base into a mode, a duration, a cost band, apps and a citation — **or refused honestly**.

> **The one rule.** There is no distance heuristic, no average city speed, no "about 20 minutes".
> The brief says never fabricate precision, and the way to hold that line is to have nowhere in
> `RouteChoicePolicy` that could. The ladder ends in `UNKNOWN`, not in a guess.

## 1. Schema — migration V28. Next free: **V29**

`itinerary_leg` — `from_item_id` → `to_item_id`, both FKs into V27's `itinerary_item`. The anchors
were already there: `ordinal` is gapless and in clock order, so the chain is `item[k] → item[k+1]`.

| Column | Note |
|---|---|
| `resolution` | **`NOT NULL`** — how the numbers were arrived at. See §2 |
| `transport_mode` | `TransportKind`, **nullable** — the curated vocabulary has no "unknown" member, so an unknown leg carries `NULL` rather than a synthetic constant |
| `duration_minutes` | Nullable, and only for `UNKNOWN` |
| `cost_band` | Ordinal band, never an amount — the KB curates bands for modes, not fares |
| `route_segment_id` | The citation. `ON DELETE SET NULL`: recurating a template must not delete a plan |
| `recommended_app_ids` | `uuid[]`, already filtered through suppression (§3) |

Three CHECKs carry the honesty rules, mirroring the record: an `UNKNOWN` leg claims nothing
(`ck_itinerary_leg_unknown_claims_nothing`), everything else has a duration *and* a mode
(`ck_itinerary_leg_resolved_has_duration`), and only a `CURATED_SEGMENT` may cite a segment
(`ck_itinerary_leg_segment_only_when_curated`). Enforced in both places because this is the field a
traveller acts on.

## 2. Resolution priority — the ladder

Four rungs, tried in order, each a weaker claim than the one above. Fixed order rather than scoring:
a curated fact beats an estimate and an estimate beats a guess, while a scoring function would let a
well-tuned weight put a guess above a fact.

| # | `LegResolution` | When | Cites a segment? |
|---|---|---|---|
| 1 | `CURATED_SEGMENT` | A segment between the two areas, not flagged `estimated` | **Yes** |
| 2 | `SAME_AREA_WALK` | Both stops in one curated area — a topology claim, not a distance guess | No |
| 3 | `AREA_ESTIMATE` | A segment exists but the corpus flagged it `estimated` | No |
| 4 | `UNKNOWN` | Nothing above applied | No |

Rungs 1 and 2 **can never compete**: `RouteSegment` refuses a segment from an area to itself and
`SampleSeedDomainCheck` rejects a seed that tries (F-44). Pinned by
`cannotHoldACuratedSegmentWithinASingleArea`.

**Segments resolve in both directions.** A corpus that curates Shibuya→Asakusa has described the
return trip too; requiring both rows would double the curation burden to state a symmetric fact. Ties
break by duration, then by id, so the answer is stable.

**A segment whose `transportModeId` is not in the destination's curated modes is refused, not
defaulted.** Falling back to `WALK` would put a traveller on foot across a city.

### Fallback semantics

`SAME_AREA_WALK` uses `ItineraryLeg.SAME_AREA_WALK_MINUTES` — a **declared constant**, not a
computation. Deriving it from coordinates would imply a surveyed route; declaring it says plainly
that this is the standing assumption about what "one area" means.

`UNKNOWN` is **shown, not hidden**. A leg quietly omitted reads as "these two stops are adjacent"; a
leg labelled unknown tells the traveller to check for themselves — the only safe thing to say when
the corpus is PARTIAL (ADR 010 §1). `ItineraryLeg.needsUserAttention()` is the flag to render on.

## 3. Local app rules (UC-C3-11, UC-K14)

`TravelAppSelector` is the single place suppression is applied, so no call site can forget.
Recommending Uber for a ride in Shanghai is not a slightly worse suggestion than 滴滴 — it is an app
the traveller cannot use while standing on a kerb.

| Leg mode | Categories offered |
|---|---|
| `METRO` `TRAIN` `BUS` `TRAM` `FERRY` | `TRANSIT` + `PAYMENT` |
| `TAXI` `RIDESHARE` `CAR` | `RIDEHAILING` + `PAYMENT` |
| `WALK` `BIKE` | `NAVIGATION` |
| *(unknown leg)* | none |

`PAYMENT` rides along with public transport deliberately: the IC card is the thing a traveller does
not know to ask for. Suppression matches on **`slug`**, not display name — a locale-dependent name
would fail exactly where it matters most.

## 4. Route freshness

There is none, and that is deliberate. A leg is a snapshot of what the KB held at resolution time,
and `resolution` says how firm that was. Re-running `resolveForItinerary` **replaces** a day's legs
(never appends), which is how a plan picks up newly curated routes. Task 40's TKB refresh is what
would trigger it.

Static fixtures do **not** claim real-time accuracy — no live traffic, no live departure times. Say
"about 35 minutes" in any UI, never "arrives 14:32".

## 5. Provider interface (for a future live adapter)

The seam is `RouteChoicePolicy.resolve(LegRequest)`. `LegRequest` carries everything a decision needs
and nothing that could reach the network, so a live adapter belongs **above** it — in
`RouteResolutionService`, contributing extra `RouteSegment`s to the corpus before the policy runs,
not inside the policy. That keeps the "no fabricated precision" property checkable: the pure package
*cannot* call a provider.

A live adapter must mark what it returns `estimated = false` only if the provider genuinely measured
it, and the resolution it produces still passes through the same ladder.

## 6. What tasks 30, 31 and 35/36 need

**Task 30 (itinerary agent).** Call `RouteResolutionService.resolveForItinerary` after
`ItineraryScheduler` produces a plan. You may narrate a leg but not invent one — the LLM must not
choose modes or apps (brief §Do not), and everything it needs is already on the record.

**Task 31 (UI).** `findForItinerary` returns every leg. Render `needsUserAttention()` legs as an
explicit "check locally" chip rather than hiding them, and show `recommendedAppIds` as the app chips
in UC-C3-11's timeline. A leg with no duration has none to show — do not substitute a dash that reads
as zero.

**Tasks 35/36 (chat).** `describeRoute(destinationId, fromPoiId, toPoiId)` is UC-C3-12's
`get_route`, tool-ready. It runs the **same ladder**, so chat and timeline cannot disagree —
`Optional.empty()` means the corpus could not answer and must be rendered as "I do not know", never
as silence.

## 7. Known gaps

- **No POI-level routing.** Segments are area→area, matching the KB. Two POIs in different areas use
  their areas' segment; two in the same area are one `SAME_AREA_WALK` regardless of actual distance.
- **No multi-hop composition.** If A→B and B→C exist but A→C does not, the leg is `UNKNOWN` rather
  than the sum. Chaining durations would invent an interchange time nobody curated.
- **No alternatives.** `uq_itinerary_leg_pair` permits one leg per ordered pair; offering "metro or
  taxi" needs a discriminator column and a UI that can present the choice.
- **Sample corpus has few segments,** so most sample-destination legs resolve to `SAME_AREA_WALK` or
  `UNKNOWN`. That is F-34 showing through, not a defect here.
