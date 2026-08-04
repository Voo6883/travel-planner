# Research chat tools & evaluation handoff (Task 27 → Tasks 28–30 / 36)

Task 27 extends trip chat through C2 and adds the deterministic research eval gate (S4-9)
plus research-complete mail (S4-8).

## Tool gates (`TripChatTools`)

| Tool | Allowed when | Mutates? |
|---|---|---|
| `start_research` | `BRIEF_COMPLETE` | Yes → `ResearchJobService.start` |
| `get_research_status` | `RESEARCH_QUEUED` / `RUNNING` / `READY` / `DESTINATION_SELECTED` | No |
| `get_recommendations_summary` | `RESEARCH_READY` / `DESTINATION_SELECTED` | No |
| `select_recommendation` | `RESEARCH_READY` | Yes → `ResearchRecommendationService.select` |
| `get_destination_guide` | `RESEARCH_READY` / `DESTINATION_SELECTED` | No (KB) |
| `get_travel_apps` | `RESEARCH_READY` / `DESTINATION_SELECTED` | No (KB) |

Intake tools (`update_trip_brief`, `answer_clarification`) unchanged on DRAFT /
CLARIFICATION_NEEDED.

## Confirmation policy (PLAN §3.2)

| Tool | Schema gate |
|---|---|
| `start_research` | `{ "user_confirmed": true }` — refused unless true. Prompt policy: ask *"Shall I research options now?"* when hesitant; set true only after agreement or a clear "research now" ask. |
| `select_recommendation` | `confirmation=explicit` + `recommendation_id` after user confirms a pick; `confirmation=just_pick` (no id) when they said *"just pick for me"* → highest rank. |

Progress explanations must use only persisted `job.status` / `progress_pct` from
`get_research_status` — never invent percentages or completion.

## SSE domain events (query sync)

| Event | Payload | Frontend invalidation |
|---|---|---|
| `research_started` | `trip_id`, `job_id` | trip detail + `research.job(tripId, jobId)` |
| `destination_selected` | `trip_id` | trip detail + `research.recommendations(tripId)` |

## Research-complete mail (UC-N04 / UC-C2-08)

- Template: `mail/templates/research-complete.{html,txt}` (`MailTemplate.RESEARCH_COMPLETE`)
- Trigger: `ResearchReadyEvent` after `markCompleted` commits → `ResearchCompletionMailService`
- **v1 preference/offline policy:** send iff `user.emailVerified && user.enabled`. No presence
  channel or notification-preference column exists yet; async completion itself is the offline
  case. Documented conflict: brief says "preference/offline rules" but no such columns exist —
  do not invent a preference column without ADR.
- **Idempotency:** `research_job.completion_mail_sent_at` (migration **V26**); claimed via
  `ResearchJob.claimCompletionMail` under optimistic lock.

## Evaluation harness (S4-9)

- Package: `ai/eval/` — `ResearchEvalHarness` + metrics (schema validity, source coverage,
  unsupported claims, tool-call budget, latency, estimated cost)
- Fixtures: `src/test/resources/ai/eval/cases.json` — categories: interests, budget, seasonality,
  unsupported_data, contradictory_sources, missing_provenance, tool_loops, prompt_injection,
  malformed_output, no_result, provider_timeout, selection_policy
- CI: `ResearchEvalHarnessTest` inside `./gradlew test` (fail-closed). Live eval separately approved.
- Thresholds (CI defaults): coverage ≥ 1.0, unsupported ≤ 0, tool calls ≤ 40, latency ≤ 5s,
  estimated cost ≤ 0.0 (stub)

## Selected-destination context for itinerary (tasks 28–30)

Unchanged from task 26 after chat select: `trip.status === DESTINATION_SELECTED`,
`trip.selected_recommendation_id` → ranked row (`destination_id`, guide, `source_refs`, …).

## Migrations

**V26** — `research_job.completion_mail_sent_at`. Next free migration: **V27**.

## Out of scope

Itinerary/booking tools; inventing FULL KB facts; live provider eval; notification preference UI.
