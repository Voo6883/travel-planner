# Itinerary domain and scheduling handoff (Task 28 → Tasks 29, 30, 31)

Task 28 lands the **deterministic** half of C3: the itinerary aggregate, its schema, and the
scheduling algorithm that decides whether a day can actually be walked — before anything narrates
it.

> **The division this task exists to enforce.** Task 30's agent chooses *which* places to visit and
> writes the prose. Whether the resulting day is feasible is arithmetic, and arithmetic performed by
> a language model is arithmetic nobody can check. Feasibility is decided in
> `domain/algorithm/scheduling/` and nowhere else.

## 1. Schema — migration V27. Next free: **V28**

| Table | Role |
|---|---|
| `itinerary` | Aggregate root. One live plan per trip (`uq_itinerary_trip`), `@Version` per ADR 008 |
| `itinerary_day` | Day container — `day_number`, `day_date`, `area_id`, and the window it was scheduled against |
| `itinerary_item` | One scheduled block — `scheduled_start`/`scheduled_end`, `duration_minutes`, `poi_id`, `category`, `source_ref` |

`itinerary_leg` is **deliberately absent** — see §5.

**Times are `time`, not `timestamptz`.** A block is a wall-clock intent ("the temple at 09:30") in
the itinerary's own zone. An instant would pin it to a UTC offset and shift the whole plan the next
time that zone's DST rules changed. `itinerary.timezone` is the one thing that converts a block to
an instant, and it is **copied** from the destination at generation time rather than followed, so
correcting a destination row never silently moves an existing plan.

## 2. Domain

```
domain/model/            Itinerary · ItineraryDay · ItineraryItem
domain/enums/            ItineraryStatus (DRAFT | READY) · ItineraryItemCategory (SIGHT | FOOD | TRANSIT | FREE_TIME)
domain/port/             ItineraryRepositoryPort
domain/algorithm/scheduling/   ItineraryScheduler · ItineraryDayPlanner · DayPlan · SchedulingResult · SchedulingNotice
```

**Invariants, and where they live.** Each is asserted in a constructor, so a plan that exists is a
plan that holds:

| Rule | Enforced by |
|---|---|
| No two blocks share a minute (touching is fine) | `ItineraryDay` — sorted intervals, names both colliding blocks |
| Every block inside its day window | `ItineraryDay` |
| Day numbers 1..n, no gaps, each date = start + ordinal | `Itinerary` |
| A `READY` plan has ≥1 day and no empty day | `Itinerary.requireReadyIsPublishable` |
| `timezone` is a resolvable, region-based IANA zone | `Itinerary` (`UTC+9` is refused — a fixed offset ignores DST) |
| A `SIGHT` is grounded in a POI (UC-C3-03) | `ItineraryItem` |
| `duration_minutes` agrees with the scheduled window | `ItineraryItem` |

The READY rule cannot be a database CHECK — it spans three tables and asks questions no single row
can answer — so it lives in the constructor every write path goes through. **`DRAFT` is held to none
of it**: a half-built plan that cannot be saved cannot be resumed.

## 3. The scheduler

`ItineraryScheduler.schedule(TripPlanRequest)` → `SchedulingResult`. **This is the stable interface
task 30 submits candidate plans through.**

Greedy, single forward pass, no backtracking. A day holds ~6 blocks from a few dozen already-ranked
candidates; the gap between an optimal schedule and a good one is a few minutes of walking, while
the gap between a deterministic schedule and a clever one is whether the same trip replans
identically tomorrow. `O(n log n)` — the sort dominates.

**Order of operations, and why:**

1. **Meals reserved first** (UC-C3-06). Anything placed before them can only be worked around;
   reserving late is how a day ends up with three temples and nothing to eat.
2. **Sights fill the gaps**, in the caller's priority order, each pushed past its opening time *and*
   past the caller-supplied travel buffer.
3. **Whatever did not fit becomes a `SchedulingNotice`** — never a silent omission.

**Three things are inputs, not lookups:** travel buffers (task 29 owns routing), opening hours
(curated where known), and visit durations. The scheduler never reaches for a clock either — dates
and times arrive as arguments, which is what makes the tests table-driven and the output
reproducible.

### Typed outcomes

`DayPlan.Outcome` is **derived** from the two lists, never asserted by a caller:

| Outcome | Meaning |
|---|---|
| `FEASIBLE` | Everything offered was placed, no caveats |
| `PARTIAL` | Usable, with something left out or uncertain — **the common case on a PARTIAL corpus** |
| `INFEASIBLE` | Nothing placed, **or nothing grounded** — a day of only held slots is a blank page with a label |

`SchedulingNotice.Reason`: `UNKNOWN_HOURS`, `CLOSED_ALL_DAY`, `NO_OPEN_SLOT`, `PACE_LIMIT_REACHED`,
`DAY_WINDOW_EXHAUSTED`, `MEAL_SLOT_UNFILLED`, `DUPLICATE_CANDIDATE`.

**Unknown hours place the block and flag it.** ADR 010 §1's corpus is PARTIAL for every sample
destination; refusing everything uncurated would return an empty plan and call it infeasible, which
is a worse answer than a plan carrying a visible caveat. `SchedulingResult.hasUnknownData()` is how a
caller surfaces it.

## 4. Persistence

`ItineraryPersistenceService` — `@TransactionalWrite` to save, `@Transactional(readOnly = true)` to
read. **Scheduling happens before the transaction opens**: holding a connection while an algorithm
runs is the shape of F-41, which cost this project eight hung threads for twenty-five minutes.

`ItineraryRepositoryPort` writes **the whole aggregate, always**. There is no `saveDay` — the
invariants span all three tables, and a port that could write one day in isolation would be a way to
persist a plan no constructor ever checked. Regeneration (UC-C3-04) replaces in place, with
`orphanRemoval` clearing days the new plan does not have.

## 5. What tasks 29, 30 and 31 need

**Task 29 (routes and mobility).** `itinerary_leg` is yours. Items already carry the anchors a leg
joins on — `itinerary_item.id` and a **gapless `ordinal` in clock order** — so V28 adds legs without
reshaping anything here. The scheduler consumes travel time as
`SchedulingCandidate.travelMinutesFromPrevious`; wire your lookup into the caller that builds
candidates, not into the algorithm.

**Task 30 (itinerary agent).** Build `SchedulingCandidate`s from the KB, call
`ItineraryScheduler.schedule`, and narrate what comes back. You may not label a plan ready — only
`Itinerary` decides that, and it refuses. Grouping days by area (UC-C3-07) is the caller's job: the
scheduler honours the clustering it is given and does not choose it, because choosing needs KB data
this package deliberately cannot reach.

**Task 31 (UI).** Read `Itinerary` through `ItineraryPersistenceService.findForTrip`. Every block has
`scheduled_start`/`scheduled_end` for the vertical timeline (UC-C3-08) and a `source_ref` for its
citation (UC-C3-03). A `FOOD` item with no `poi_id` is a **held meal slot nothing could fill** —
render it as reserved time, not as a restaurant.

## 6. Out of scope, and why

No HTTP surface, no frontend, no LLM, no route provider — task 28's brief excludes all four. There
is no `POST .../itinerary` yet; task 30 owns generation and the endpoint that triggers it.

## 7. Known gaps

- **A day cannot wrap midnight.** `LocalTime` alone cannot express "until 01:00 tomorrow", and
  `plusMinutes` wraps silently, so a block that would cross midnight is refused rather than wrapped
  (pinned by `refusesABlockThatWouldWrapPastMidnight`). Late-night itineraries need a day-offset
  column before they can be expressed.
- **Area clustering is honoured, not computed.** UC-C3-07's grouping is the caller's decision.
- **`TRANSIT` blocks are only emitted when supplied as candidates.** The scheduler never invents
  travel time — that is task 29's data.
