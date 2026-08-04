# Trip chat intake tools handoff notes (Task 22 → Tasks 23/27)

Task 22 adds the trip-thread intake tools that let the agent edit the brief the server actually
holds. Everything lives in the vertical slice `application/tripchat/`. `ChatTurnService` routes
planner turns to `PlannerChatOrchestrator` and trip turns to `TripChatOrchestrator`.

## Trip context block in the prompt (`TripChatPrompt`)

Before each trip turn the orchestrator loads the current `TripBriefView` and appends a second
`SYSTEM` block to the turn's prompt. It is authoritative and says so, because a long trip thread's
conversation can lag the stored brief by several edits:

- `trip.status` and `brief.expected_version` (= `brief.version()`), taken from the view just read
- the brief fields: `destinations`, `surprise_me`, `dates`, `date_flexibility`, `departure_city`,
  `budget`, `party`, `interests`, `pace` (absent values render as `not set`)
- `outstanding_question_ids` — the exact ids `answer_clarification` will accept
- instructions: call `update_trip_brief` / `answer_clarification` with the given `expected_version`;
  never invent travel facts; only claim a field was saved once a tool result confirms it

The block is appended, not merged into the base instruction (`ChatTurnService.TRIP_SYSTEM_PROMPT`),
so neither side has to know how the other was worded — `Prompt.systemText()` joins them.

## Tool registry and the status gate (`TripChatTools`)

`specsFor(TripStatus)` returns the allowed tools only:

| Status | Tools offered |
|---|---|
| `DRAFT` | `update_trip_brief` |
| `CLARIFICATION_NEEDED` | `update_trip_brief`, `answer_clarification` |
| anything else (`BRIEF_COMPLETE`, `RESEARCH*`, `ARCHIVED`, …) | none |

Schemas are hand-written JSON Schema strings with `additionalProperties: false` and snake_case
property names matching the OpenAPI wire style. The gate is enforced twice: the model is only
*offered* the allowed tools, and `TripChatToolService.requireStatusAllows` re-checks
`TripChatTools.isAllowed(status, toolName)` before any write, so a status-gate refusal is a
`validation_failed` that mutates nothing.

### Extension procedure for Task 23 (`start_research`) / Task 27

Task 27 landed the research tools. See [`docs/RESEARCH-CHAT-EVAL-HANDOFF.md`](RESEARCH-CHAT-EVAL-HANDOFF.md)
for gates, confirmation policy, SSE events, mail, and the eval harness.

Historical procedure (still valid for later tools):

1. Add a `NAME` constant and a JSON Schema string constant in `TripChatTools`.
2. Add the tool to `specsFor` under the statuses where it is legal, and extend `isAllowed`.
3. Add an `Args` record with schema-validated `parse` — throw `ValidationFailedException`.
4. Add a write/read method on `TripChatToolService` or `TripChatResearchToolService`.
5. Dispatch in `TripChatOrchestrator.run`, emit domain events for client invalidation.

## Approved statuses

- `update_trip_brief`: `DRAFT`, `CLARIFICATION_NEEDED`
- `answer_clarification`: `CLARIFICATION_NEEDED` only

The write itself is delegated to `TripBriefService.save` / `answerClarification`, which re-validate
every value and enforce the ADR 008 optimistic lock. A stale `expected_version` surfaces as
`version_conflict`; an uncurated destination as `destination_not_covered`; both are added to
`ChatTurnService.STREAMABLE_ERROR_CODES` so they reach the client as `event: error` rather than
aborting the stream.

## Domain event and query invalidation

After a committed edit the orchestrator emits `LlmEvent.DomainEvent("brief_updated", {trip_id})`.
`ChatTurnService.domainEvent` turns it into `ChatStreamEvent.BriefUpdated(tripId)` →
SSE `event: brief_updated` (mirrors `trip_created`; never inferred from prose, ADR 007).

Frontend:

- `chat-events.ts` parses `brief_updated` like `trip_created` (`trip_id`).
- `chat-state.ts` sets `briefUpdatedTripId` on the event and clears it on `stream_opened`, so a
  second turn editing the same trip re-fires the signal.
- `ChatPanel` exposes `onBriefUpdated?(tripId)`; `TripChatPanel` wires it to invalidate both
  `queryKeys.trips.brief(tripId)` **and** `queryKeys.trips.detail(tripId)`, so the brief editor
  beside the chat re-fetches what the agent saved.

## Tool loop (`TripChatOrchestrator`)

Unlike the planner's single terminal `create_trip`, editing a brief is a conversation. When a
provider round ends on `StopReason.TOOL_USE` after a tool committed, the orchestrator feeds the tool
result back and streams again, up to `MAX_ROUNDS` (one initial round + three follow-ups).
Intermediate `Done(TOOL_USE)` frames are swallowed; only the final terminal frame reaches the
client. A tool refusal is terminal: the `StreamError` is forwarded and the loop stops.
