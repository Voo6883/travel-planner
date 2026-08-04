# Itinerary generation agent handoff (Task 30 → Task 31, 35/36)

Task 30 wires C3 generation end to end: the agent proposes, guardrails ground it, task 28's
scheduler and task 29's route policy decide whether it works, and only then is anything written.

> **The single rule this task exists to enforce.** The model chooses *which* places and *in what
> order*. It does not decide times, durations, travel, or feasibility. That is not a prompt
> instruction — `ItineraryProposal` has **nowhere to put a time**, so a model cannot express a
> schedule even if asked to, and `Itinerary` refuses to be `READY` unless every day holds something.

## 1. The pipeline

```
ItineraryGenerationService.generate(trip, brief, destinationId, start, end)

  status gate ──── DESTINATION_SELECTED only
       │
  load corpus ──── KnowledgePort: POIs (≤60), areas, destination
       │
  agent.propose ── ItineraryAgentPort  ← the only step a live model replaces
       │           (guardrails run inside the adapter, per the port contract)
       │
  scheduler ────── ItineraryScheduler (task 28) — authoritative on feasibility
       │
  persist ──────── ItineraryPersistenceService, then routes, then status
       │
  routes ───────── RouteResolutionService (task 29)
       │
  status ───────── ItineraryStatusWriter → ITINERARY_READY
```

**Nothing slow runs inside a transaction.** The brief forbids calling the LLM or route tools inside
one; F-41 is why this project takes that seriously. The only transactional steps are the two writes
at the end.

## 2. Prompt / schema version

| Field | Stub value | Where |
|---|---|---|
| `promptTemplateId` | `itinerary-stub` | `StubItineraryAgent.PROMPT_TEMPLATE_ID` |
| `promptVersion` | `1` | `StubItineraryAgent.PROMPT_VERSION` |
| `modelName` | `stub` | `StubItineraryAgent.MODEL_NAME` |

Carried on every `ItineraryProposal`. A live agent supplies its own and must bump `promptVersion`
whenever the prompt changes in a way that would alter output.

## 3. The repair loop

`MAX_ATTEMPTS = 2` — the original plus one repair, matching `StructuredOutputRunner`'s budget.

| Failure | Retried? | Why |
|---|---|---|
| Ungrounded proposal (`ValidationFailedException`) | **Yes**, once | A second sample may differ |
| Not schedulable | **Yes**, once | Same |
| Provider unavailable (`AiProviderException`) | **No** | Retrying a dead provider is a slower error |

Two is not timidity: a model that produced an ungroundable plan twice is not one attempt away from
success, and each round is a billed call and a longer wait. `ItineraryGenerationOutcome.attempts()`
publishes the count, which is the DoD's "bounded and observable".

## 4. Typed failures

`ItineraryGenerationOutcome` is a **value**, not an exception, for the failure path — these are
ordinary answers on a PARTIAL corpus, not crashes.

| `Failure` | Meaning |
|---|---|
| `WRONG_TRIP_STATUS` | Not `DESTINATION_SELECTED` — a caller ordering problem |
| `NO_SELECTED_DESTINATION` | Nothing chosen yet |
| `INSUFFICIENT_KNOWLEDGE` | Too little curated to plan against (F-34 showing through) |
| `UNGROUNDED_PROPOSAL` | Fabricated ids / duplicates / wrong day count, after the repair |
| `NOT_SCHEDULABLE` | The scheduler refused every proposal — the validators winning |
| `PROVIDER_UNAVAILABLE` | Provider failed or timed out |

## 5. Guardrails (grounding)

`ItineraryOutputGuardrails`, applied **inside the agent adapter** — `application/` may not import
`ai/`, and ArchUnit enforces it. A live agent inherits the same contract.

- Every `poi_id` must be in the request's candidate set.
- Every `area_id` must be a curated area — otherwise V27's FK rejects the write with a message
  naming no cause.
- No POI twice **across the whole trip** (the scheduler only dedupes within a day).
- Exactly one proposed day per calendar day.

**The whole proposal is rejected, never the offending stop.** Dropping it silently leaves the
narrative describing a temple that is no longer in the plan.

## 6. Regeneration semantics

**Replace, do not version.** `uq_itinerary_trip` permits one live plan per trip and
`ItineraryRepositoryAdapter.save` rewrites in place with `orphanRemoval`, so re-generating a trip
supersedes its plan; a five-day plan replaced by a three-day one leaves nothing behind. Legs are
replaced per day by `replaceForDay`.

**Not yet implemented, and deliberately so:** preserving user-approved constraints across a
regeneration (task 30 scope line) needs somewhere to record which parts a traveller pinned, and no
such column exists. Building one before task 31 knows what "approved" looks like in the UI would be
guessing. **Task 31 owns defining it**; today a regeneration is a clean rebuild.

## 7. `patch_itinerary` — foundation only

Not built. The scope calls for a "structured-diff command/schema foundation", and the honest state is
that the foundation is `ItineraryProposal` plus the replace-in-place persistence above: a patch is
expressible as a proposal that reuses most of the previous plan's POIs. **The command type itself is
task 31's**, because its shape depends on the edit affordances the UI offers, and inventing an
opcode vocabulary nothing consumes would be a schema to maintain with no caller.

> The tool name is `patch_itinerary`; HTTP stays **PUT** (PLAN §3.2 note). It is an LLM tool name,
> not a verb.

## 8. What task 31 consumes

- `ItineraryGenerationService.find(tripId, userId)` → the plan.
- `RouteResolutionService.findForItinerary(itinerary)` → its legs.
- `ItineraryGenerationOutcome.failure()` → what to tell the traveller when there is no plan. Each
  value is a different sentence; do not collapse them into "something went wrong".

## 9. Known gaps

- **No live agent.** `StubItineraryAgent` is the wired bean — the same standing situation as
  **F-52** for C2, and for the same reason: a live agent needs a provider key, which CI does not
  have. The stub is what the tests exercise, and it proves the pipeline rather than the prose.
- **Visit durations are a policy default** (90 min sight / 60 min food) in
  `ItineraryGenerationService.defaultVisitMinutes`, because the corpus curates none. A stated
  assumption in one place, not a measurement.
- **No HTTP surface.** Task 30's brief excludes UI; there is no `POST .../itinerary` yet.
- **Opening hours pass through as curated** — absent stays absent, so the scheduler reports
  `UNKNOWN_HOURS` rather than this layer inventing a 09:00.
