# Destination ranking handoff (Task 24 → Task 25/26)

Task 24 adds the **pure C2 fit-score DSA** under `domain/algorithm/ranking/`. It ranks destination
candidates from typed signals — never from LLM prose — and returns structured exclusions when a
place cannot be recommended (UC-C2-05, UC-C2-11).

## Entry point

```java
RankingResult result = new DestinationRanker().topK(new RankingInput(
        candidates, brief, ScoringWeights.defaults(), topK, Instant.now()));
```

| Type | Role |
|---|---|
| `DestinationCandidate` | Bounded, pre-aggregated signals (categories, ≤12 seasonality rows, price observations, area count) |
| `TripBriefDetails` | Interest / dates / budget / party / pace |
| `ScoringWeights` | `w1…w4` — defaults below |
| `RankingResult` | `ranked[]`, `excluded[]`, `noConfidentResult`, `algorithmVersion` |

**Version string:** `destination-ranker-v1` (`DestinationRanker.ALGORITHM_VERSION`). Persist this
beside recommendations in task 25/26 so score explanations stay aligned with the formula that
produced them.

## Default weights (PLAN §4.1.2)

| Term | Weight | Signal |
|---|---|---|
| Interest match | **0.35** | Fraction of `brief.interests` whose `PoiCategory` appears in the candidate |
| Seasonality fit | **0.25** | Mean of weather / crowd / price-band scores for months in `brief.dates` |
| Price fit | **0.25** | Under-budget fit from estimated trip cost vs `brief.budget` |
| Area coverage | **0.15** | `areaCount / areasNeeded(party, pace)` capped at 1 |

Weights are configurable via `ScoringWeights`; they must be non-negative and sum to a positive total.
`fitScore = (Σ wi·si / Σ wi) · freshnessFactor`.

## Cost estimate (price term)

Uses the latest observation per category (by `observedOn`):

```
rooms = ceil(adults / 2)
est = HOTEL_NIGHT · nights · rooms
    + MEAL_MID_RANGE · days · party.total · 2
    + TRANSIT_DAY_PASS · days · party.total
```

Missing any of the three categories → soft missing signal (neutral 0.5 + confidence penalty), not a
hard exclude. Currency mismatch with the budget → `CURRENCY_MISMATCH`. `est > budget` →
`BUDGET_EXCEEDED`.

## Hard filters (before ranking)

| Reason | When |
|---|---|
| `NOT_RANKING_ELIGIBLE` | `coverageLevel != FULL` (ADR 010 §4) |
| `NO_SEASONALITY_FOR_DATES` | Brief has dates but no overlapping seasonality month |
| `CURRENCY_MISMATCH` | Price currency ≠ budget currency |
| `BUDGET_EXCEEDED` | Estimated cost strictly greater than budget |
| `LOW_CONFIDENCE` | Scored but `confidence < minConfidence` (default `0.45`) |

Exclusions are first-class on `RankingResult.excluded` — never only in narrative text.

## Tie-break (stable)

1. `fitScore` descending  
2. `confidence` descending  
3. `slug` ascending  
4. `destinationId` ascending  

## Freshness

Seasonality and price provenance use `KnowledgeDataClass.SEASONAL_PRICING` (365d TTL). Stale fraction
applies up to a **30%** multiplicative penalty (`freshnessFactor`).

## Complexity

Per-candidate work is linear in the (bounded) seasonality/price lists. Selection is
`O(n log n)` sort + top-K (`TopKSelector`). Curated destination sets are ≪ 500; a heap is not
warranted until measured.

## What task 25 must do

1. Load FULL destinations (and only those) via `KnowledgePort` — do **not** dump unbounded POI tables
   into the ranker; project to `DestinationCandidate`.
2. Call `DestinationRanker` **outside** `@Transactional` / after retrieval.
3. Persist `algorithmVersion` + `ScoreBreakdown` fields with each recommendation.
4. Map `noConfidentResult` to the typed empty API (not an LLM apology).
5. LLM writes `rationale` / `traveler_guide` only — never invents `fitScore`.

## Out of scope here

Research agent, recommendation persistence, Redis/queue, chat `start_research` tool, UI.
