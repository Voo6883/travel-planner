# Planner chat handoff notes (Task 21 → Task 22)

## Tool schema

Planner surface offers **only** `create_trip`:

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "name": { "type": "string", "maxLength": 120 }
  }
}
```

- Registry: `application/planner/PlannerTools`
- Args validated in `CreateTripArgs` before any write
- Unknown tool names are refused (`validation_failed`); never executed from free text

## Trip naming

`TripNamer.nameFor(args)`:

1. Use trimmed non-blank `name` from validated args when present
2. Otherwise `"New trip"`

Owner is always `UserContext.userId()` — the model cannot choose user or persistence ids.

## Handoff transaction (`CreateTripHandoffService`)

Single `@TransactionalWrite`:

1. Load owned conversation; refuse archived
2. If already linked to a trip → idempotent return of that `trip_id`
3. Else create `Trip` + empty `TripBrief`, `conversation.linkToTrip`, end open `PlannerSession`
4. Append `tool_call`, `tool_result`, and `lifecycle_event` rows (`{"trip_id":"…"}`)

LLM/HTTP stay outside this transaction. After commit the orchestrator emits
`LlmEvent.DomainEvent("trip_created", {trip_id})` → SSE `trip_created`.

## Context for Task 22

- Trip chat continues on the **same** conversation id (now `scope=TRIP`)
- Planner tools must not be offered after handoff (`PlannerChatOrchestrator` is planner-only)
- Trip-level tools (`update_trip_brief`, research, …) belong in Task 22’s registry
- Empty brief is intentional; applying a validated brief from conversation text is Task 22
  (`update_trip_brief`) rather than a silent post-create extract
