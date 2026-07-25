# ADR 008: Optimistic concurrency and partial updates without PATCH

## Status

Accepted

## Context

`PLAN.md` §6.1 forbids the HTTP `PATCH` verb; all mutations are `PUT` (full body) or a dedicated
`POST` action. Simultaneously, §3.2 lets the LLM agent mutate `TripBrief` and `Itinerary` through
tools, and §4.2 auto-saves the brief form on a **300 ms debounce**.

These combine into a structural lost-update defect. The user edits the brief form while the agent
applies `update_trip_brief` from a streaming turn; both issue a whole-object `PUT`; whichever lands
second silently discards the other's changes — including server-computed fields such as
`itinerary_leg` and `source_ref`. The same race applies to `patch_itinerary` during C5 enhancement.

`PLAN.md` §8 defines no `version` column, and §6.1 defines no concurrency header or conflict code.
The product's central claim — that chat and structured UI are "synced mirrors" — has no mechanism
behind it.

## Decision

### 1. Optimistic locking on every agent-mutable aggregate

`version integer not null default 0`, mapped with JPA `@Version`, on:

`trip`, `trip_brief`, `itinerary_day`, `itinerary_item`, `booking`

Every read that precedes a write returns `version`. Every write carries the version the client
based its edit on.

### 2. Version travels in the body, not in a header

```jsonc
// PUT /api/v1/trips/{tripId}/brief
{ "expected_version": 7, "destinations": ["Kyoto"], /* ... */ }
```

| Rule | Detail |
|---|---|
| Mismatch | `409` + `{ "code": "version_conflict", "details": { "current_version": 9 } }` |
| Response | Every mutation returns the **new** full resource with its incremented `version` |
| Missing `expected_version` | `400 validation_failed` — never treated as "force overwrite" |

Body-carried version is chosen over `ETag`/`If-Match` because it is expressible in OpenAPI, flows
through codegen into the typed client automatically, and keeps §6's "strict typing end-to-end"
thesis intact. Header-based concurrency would require hand-plumbed header handling in exactly the
generated-client layer the plan forbids hand-editing.

### 3. Partial updates are POST actions with typed diffs

The no-`PATCH` rule stands, but full-body `PUT` is **not** the answer for large aggregates. A
14-day itinerary must not be re-sent to move one item.

| Operation | Endpoint |
|---|---|
| Move / reorder / replace an item | `POST /api/v1/trips/{tripId}/itinerary/actions/apply-diff` |
| Answer clarification | `POST /api/v1/trips/{tripId}/brief/actions/answer-clarification` |

Each action takes a **typed diff DTO** plus `expected_version`. This is what the `patch_itinerary`
LLM tool maps to — the tool name remains an LLM concept, never an HTTP verb (§3.2).

### 4. Conflict resolution rules

| Situation | Behaviour |
|---|---|
| Agent write vs. user write | **No implicit winner.** The loser receives `409` and must re-read |
| Form autosave loses | Re-fetch, re-apply the user's uncommitted field edits onto fresh state, show a non-blocking "updated by assistant" notice |
| Agent tool loses | Orchestrator re-reads and retries **once**, then reports the conflict in chat rather than clobbering |
| User has focus in a field the agent changed | Do not overwrite the focused field; surface the change and let the user accept |

### 5. Frontend invalidation

Optimistic updates are **forbidden on any entity the agent can mutate** — server truth arrives
concurrently via SSE and an optimistic write racing an invalidation produces flicker. Permitted
only for pure-UI state (toggles, local sort).

`lib/query/query-keys.ts` gains a hierarchical key map (`trip`, `brief`, `itinerary`,
`recommendations`, `conversation`) and an explicit **SSE-event → invalidation** table, so a
`DomainEvent` frame ([ADR 007](007-chat-streaming-transport.md)) invalidates the right subtree.
Without this, the brief form shows stale values after the agent changes them — the single most
predictable bug in the build.

## Rationale

| Factor | Body `expected_version` (chosen) | `ETag` / `If-Match` | Last-write-wins |
|---|---|---|---|
| Expressible in OpenAPI | Yes | Header only, weakly | N/A |
| Flows through codegen | Yes | Needs hand plumbing | N/A |
| Data loss | Prevented | Prevented | **Guaranteed** |
| HTTP idiomatic | Less | More | N/A |
| Fits no-PATCH lock | Yes | Yes | Yes |

## Consequences

- Flyway migrations add `version` to five tables; all are new in Phase 0b/1, so no backfill.
- `version_conflict` is registered in the OpenAPI error catalog with an i18n key and a conflict UX
  in every editing surface.
- Testing: `PLAN.md` §4.0.2-E2 already requires optimistic-lock integration tests; this ADR makes
  concurrent **agent-vs-user** edits an explicit test case for brief and itinerary.
- `research_job` and `message` are append-only and need no version.
- Tasks affected: **06** (error catalog + contract convention), **07** (locking pattern),
  **18**, **20**, **22**, **28**, **31**, **33**.

## Alternatives considered

- **`ETag` / `If-Match`** — more RESTful; rejected because header plumbing lands in the generated
  client layer and the version becomes invisible to typed contracts.
- **Last-write-wins** — rejected; silent data loss on the product's core interaction.
- **Server-side merge (CRDT / operational transform)** — rejected as vastly disproportionate for
  single-user trips with one concurrent agent.
- **Locking the form while the agent runs** — rejected; blocks the "chat and form stay in sync"
  product promise.
- **Reversing the no-PATCH rule** — rejected; typed POST actions give the same capability with
  better validation and no locked-decision change.
