# Travel research agent handoff (Task 25 → Task 26)

Task 25 plugs the **knowledge-grounded C2 research agent** into the task-23 job platform and
persists ranked recommendations (or a typed no-confident-result) so a client that sees
`RESEARCH_READY` always has a durable outcome to fetch.

## Runtime entry points

| Seam | Implementation | Notes |
|---|---|---|
| `ResearchJobHandler` | `TravelResearchJobHandler` (`@Service` + `@RequiresDatabase`) | Wins over `NoOpResearchJobHandler` via `@ConditionalOnMissingBean` |
| `ResearchCompletionHook` | `ResearchRecommendationCompletionHook` | Persists pending outcome **inside** the COMPLETED / `RESEARCH_READY` transaction |
| `TravelResearchAgentPort` | `StubTravelResearchAgent` (default) | Wired in `ResearchAgentConfig`; application never imports `ai/` |

### Handler sequence (no TX held)

1. Revalidate trip is `RESEARCH_RUNNING` and brief is complete (`ClarificationNeeded.isSatisfied`).
2. Build bounded `DestinationCandidate`s from `KnowledgePort` (**FULL only**).
3. Run `DestinationRanker.topK` (DSA — never LLM).
4. Call `TravelResearchAgentPort.research` for rationale / `traveler_guide` narratives.
5. Stage `ResearchRunResult` in `PendingResearchOutcomeStore`.
6. Return → platform `markCompleted` → hook persists.

## Stub vs live agent

| Mode | When | Behaviour |
|---|---|---|
| **Stub** (default / CI) | Always today — `StubTravelResearchAgent` bean | Deterministic tool calls over `KnowledgePort`; no LLM; golden fixture `ai/travel-research-stub-golden.json` |
| **Live narrative** | Not wired yet | Future: stronger `@ConditionalOnProperty` on `travelplanner.ai.provider.default` ≠ `stub` |

Budgets (env-overridable):

- `travelplanner.research.max-tool-calls` (default **40**)
- `travelplanner.research.max-tokens` (default **24000**)
- Wall-clock remains the job timeout (**90s**) — the agent loop honours `Thread.interrupt()`

## Tool registry (`KnowledgeResearchTools`)

| Tool | Source |
|---|---|
| `get_destination_guide` | `KnowledgePort.findGuide` |
| `get_areas` | `findAreas` |
| `get_food_pois` / `get_pois` | `findPois` |
| `get_seasonality` | `findSeasonality` |
| `get_price_history` | `findPriceHistory` |
| `get_transport_modes` | `findTransportModes` |
| `get_route_segments` | `findRouteSegments` |
| `get_travel_apps` | `findTravelApps` |
| `web_search_supplement` | **Stub** empty results (PLAN §4.0.7) |

`ResearchOutputGuardrails` reject invented destination ids/slugs, `source_ref`s, and app slugs not
present in the tool evidence ledger.

## Persistence (migration **V25**)

### `research_run_result`

One row per completed run (`research_run_id` PK = job attribution key):

- `no_confident_result` — UC-C2-05 typed empty
- `algorithm_version` — `destination-ranker-v1`
- `prompt_template_id` / `prompt_version` / `model_name`
- `excluded_json` — DSA exclusions

### `ranked_recommendation`

One row per ranked destination (empty when `no_confident_result`):

- Score breakdown columns (`fit_score`, term scores, optional `est_cost_*`)
- `rationale`, `traveler_guide` (jsonb), `risks`, `best_window`, `source_refs` (jsonb)
- Unique on `(research_run_id, rank)` and `(research_run_id, destination_id)`

Also: unique index on `research_job.research_run_id`; FK
`trip.selected_recommendation_id → ranked_recommendation(id)` (ON DELETE SET NULL).

**Invariant:** `RESEARCH_READY` ⇒ run-result row exists (recommendations **or**
`no_confident_result=true`).

## Sample KB behaviour

Seed destinations remain **PARTIAL** (ADR 010 / task 17C). Against real sample data the ranker
correctly yields `noConfidentResult` — that is persisted as the typed empty marker, not an LLM
apology. Unit tests use FULL fixture candidates when asserting ranked rows.

## What task 26 must consume

1. `GET .../ranked-recommendations` — load latest `research_run_result` + rows for the trip when
   `RESEARCH_READY`; surface `no_confident_result` as typed empty UI.
2. Recommendation DTO fields: rationale, est cost, score/breakdown, traveler guide sections,
   risks, source refs, algorithm version.
3. `POST .../selected-recommendation` — set `trip.selected_recommendation_id` (FK now exists).
4. Do **not** recompute `fitScore` in the UI or ask an LLM for it.

## Out of scope here

List/select HTTP + research UI (task 26), `start_research` chat tool, Redis/queue, live web search
vendor, FULL TKB curation (task 41).
