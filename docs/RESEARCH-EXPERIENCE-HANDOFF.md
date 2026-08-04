# Research experience handoff (Task 26 → Tasks 28–30)

Task 26 exposes the C2 **list / select / guide** HTTP surface on top of the task-23 job platform
and the task-25 durable recommendation rows, and ships the complete `features/research/` UI.

## API routes

| Method | Path | When | Notes |
|---|---|---|---|
| `POST` | `/api/v1/trips/{tripId}/research/run` | `BRIEF_COMPLETE` | **Task 23** — 202 + job; re-run creates a new job (UC-C2-07) |
| `GET` | `/api/v1/trips/{tripId}/research/jobs/{jobId}` | owner | **Task 23** — poll `queued\|running\|completed\|failed` |
| `GET` | `/api/v1/trips/{tripId}/ranked-recommendations` | `RESEARCH_READY`, or `DESTINATION_SELECTED` with selection | **Task 26** — else `409 research_not_ready` |
| `POST` | `/api/v1/trips/{tripId}/selected-recommendation` | `RESEARCH_READY` | **Task 26** — `{ recommendation_id }` → `DESTINATION_SELECTED` |
| `GET` | `/api/v1/destinations/{destinationId}/guide?locale=en\|ms` | authenticated | **Task 26** — KB guide + areas + top POIs + apps |

### Re-run (UC-C2-07)

Already sufficient from task 23: on job failure the trip recovers to `BRIEF_COMPLETE`, and
`POST .../research/run` starts a **new** job while previous `research_job` / `research_run_result`
rows remain as history. Re-run from `RESEARCH_READY` (discard successful results) is **not**
exposed — the UI offers re-run only after recovery to `BRIEF_COMPLETE`.

### Selection semantics (UC-C2-06)

1. Recommendation must belong to the trip's **latest** `research_run_result`.
2. Atomic write: `trip.status = DESTINATION_SELECTED` **and**
   `trip.selected_recommendation_id = recommendation.id` (FK to `ranked_recommendation`, V25).
3. Concurrent writers hit ADR 008 `409 version_conflict`.
4. Selecting again after `DESTINATION_SELECTED` is `400 validation_failed` on `status`.
5. Frontend never optimistically marks selected — waits for the 200 trip body.

### Recommendation schema (wire)

`RankedRecommendations`: `trip_id`, `research_run_id`, `no_confident_result`, `algorithm_version`,
`selected_recommendation_id?`, `recommendations[]`.

Each `RankedRecommendation`: `recommendation_id`, `destination_id` / `destination_slug` /
`country_code`, `rank`, `fit_score`, `score_breakdown` (DSA terms including `freshness_factor` +
`confidence`), `est_cost?` (`Money` string amount), `rationale`, `traveler_guide`, `risks[]`,
`best_window?`, `source_refs[]`, `algorithm_version`.

`traveler_guide`: `overview`, `why_now?`, `areas[]`, `food?`, `highlights[]`, `mobility?`,
`practical?`, `local_app_pack[{usage,name,slug?}]`, `source_refs[]`.

Typed empty: `no_confident_result: true` + empty `recommendations` (UC-C2-05).

### Guide citations (amended 2026-08-04, F-50 / F-51)

`GET .../guide` returns `source_refs[]` of **`KnowledgeSourceRef`**, not `RecommendationSourceRef`.
Two schemas because they answer different questions:

| | `KnowledgeSourceRef` | `RecommendationSourceRef` |
|---|---|---|
| Source | read live from the KB | replayed from `ranked_recommendation.source_refs` jsonb |
| Fields | `+ attribution?`, `sample_data`, `stale`, `retrieved_at` | `source_ref`, `source_url?`, `field_group` |
| Can answer "stale *now*?" | yes | no — it is a snapshot frozen at research time, and does not pretend otherwise |

`source_refs` now covers **every** part of the guide payload — `overview`, `food`, `practical`,
`areas`, `pois`, `transport`, `local_app_pack` — one entry per `(source_ref, field_group)` pair that
actually backs something, deduplicated in render order. It previously emitted a single ref with
`field_group` hardcoded to `"overview"`, so everything else on the page was uncited.

`sample_data` is also a page-level boolean on `DestinationGuideDetail` and on
`GET /destinations/supported`, so a client raises ADR 010 §3's persistent banner without walking the
ref list. **No UI consumes the guide endpoint yet** — the banner is unbuilt, and whoever renders the
guide drawer owns it.

## Frontend

| Piece | Location |
|---|---|
| Panel | `features/research/components/research-panel.tsx` (`ResearchPanel` / `ResearchJobPanel`) |
| Cards / guide | `recommendation-card.tsx`, `traveler-guide-sections.tsx` |
| Hooks | `use-research-job.ts`, `use-research-recommendations.ts` |
| API | `lib/api/research-api.ts` + `schemas/research.schema.ts` (codegen types only) |
| Locales | `locales/{en,ms}/research.json` |

### Query keys

```ts
queryKeys.research.job(tripId, jobId)
queryKeys.research.recommendations(tripId)
queryKeys.research.guide(destinationId)
```

Invalidation: terminal job poll → trip detail + recommendations; successful select → trip detail
set + recommendations invalidate.

### Polling rules

- Poll job every 2s while `queued` / `running`.
- Stop on terminal status, query disable (`jobId === null`), and React Query unmount cleanup.
- Recommendations fetch only when trip status is research-ready or later (`enabled` flag).

## Itinerary input for tasks 28–30

After selection, C3 may read:

- `trip.status === DESTINATION_SELECTED`
- `trip.selected_recommendation_id` → `ranked_recommendation` row
- From that row: `destination_id`, `destination_slug`, `country_code`, `traveler_guide`,
  `source_refs`, `est_cost`, `best_window`
- Optional detail: `GET .../destinations/{destinationId}/guide` for areas / top POIs / apps /
  transport modes from the KnowledgePort (not re-ranking)

Do **not** recompute `fit_score` in the itinerary agent. Do **not** invent POIs without
`source_ref`s.

## Error code

`research_not_ready` (409) — registered end-to-end. Thrown by
`ResearchNotReadyException` when list is gated off or no durable run exists.

## Migrations

None in task 26. Persistence remains **V25** (`research_run_result`, `ranked_recommendation`,
`trip.selected_recommendation_id` FK). Next free migration stays **V26** for later tasks.

## Out of scope here

Itinerary generation, optimistic selection, fabricating sources, `start_research` chat tool
(task 27), research-complete email (S4-8).
