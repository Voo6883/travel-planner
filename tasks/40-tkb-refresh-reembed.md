# Task 40 — TKB Refresh and Re-embedding Pipeline

## Objective

Keep the Travel Knowledge Base current and its embeddings consistent, so grounded answers stay
true after the seed data ages.

> **Why this task exists.** [ADR 010](../docs/adr/010-tkb-data-sourcing-embeddings.md)
> §"Consequences" states that a task is required for the ongoing refresh/re-embed pipeline and
> that it is not currently covered. Tasks 00–39 were written before that ADR was accepted. This
> brief closes half of that gap; [Task 41](41-admin-knowledge-curation.md) closes the other half.
> Recorded as blocker **B-4** in [`EXECUTION-BASELINE.md`](EXECUTION-BASELINE.md).

## Dependencies

- Tasks 16 and 17 complete.
- Task 14 embedding abstraction available.
- Task 15 quality gates available for the CI data-quality check.

## Required reading

- [`docs/adr/010-tkb-data-sourcing-embeddings.md`](../docs/adr/010-tkb-data-sourcing-embeddings.md) §5 and §6 — authoritative for this task
- `plans/superpower/PLAN.md` §4.1.0 and §4.1.2
- `plans/TRAVEL-KNOWLEDGE-CATALOG.md`
- `tasks/16-knowledge-domain-schema.md` handoff — the columns this task writes
- `tasks/17-knowledge-seed-retrieval.md` handoff — the rows needing first re-embedding

## Scope

### Freshness evaluation

- Scheduled job evaluating TTL per data class (ADR 010 §6): `poi.opening_hours` / `price_band` 90 days · `travel_app` 180 · `price_history` / `seasonality` 365 · `destination_guide` narrative 730.
- Expired rows are flagged `stale: true` and remain retrievable — never deleted, never silently served as current.
- A report of what is stale, by destination and data class, suitable for a curator to act on.

### Re-embedding

- `content_hash` comparison on ingest; **unchanged rows are never re-embedded** — this is the cost control that makes the job runnable on a schedule.
- Re-embed only the field groups whose content changed, matching Task 16's per-field-group chunking.
- Embedding runs outside any database transaction (PLAN §4.0.2) and respects the Task 14 token budget.
- Idempotent and resumable: a job killed halfway must not leave a row with an embedding that disagrees with its `content_hash`.

### Embedding model migration

- Additive path only: new column, backfill, serve from the old index until the new one is complete, then cut over. **Never an in-place rebuild.**
- Dimension/model mismatch is rejected before write.
- A documented, repeatable procedure — this will be run rarely and under pressure.

### Adding a destination

- A documented, repeatable authoring procedure that takes a destination from `NONE` → `FULL` coverage.
- Data-quality validator runnable in CI (`PM-K03`, `PM-K04`): duplicate ids, orphan sources, invalid coordinates, missing provenance, incompatible vector dimensions, missing licence or attribution.
- Coverage cannot flip to `FULL` until the validator passes.

### Operations

- Manual trigger as well as scheduled execution.
- Structured run log: rows scanned, flagged stale, re-embedded, skipped by `content_hash`, failed.
- Failures are per-row and non-fatal; one bad row must not abort the run.

## Do not

- Do not crawl the web or introduce an unlicensed source (ADR 010 §2).
- Do not persist Google Places/Maps content — live request-time only.
- Do not use an LLM to author or "refresh" knowledge content.
- Do not delete stale rows; flagging is the contract.
- Do not re-embed unchanged content.
- Do not introduce a new scheduler runtime or queue service without an ADR.
- Do not widen destination coverage as a side effect of a refresh run.

## Validation

- Run against seeded data with no changes → zero re-embeddings, proving `content_hash` short-circuits.
- Modify one guide field group → exactly that group re-embeds; others untouched.
- Age a row past its TTL → returned with `stale: true`, and the consumer downgrades confidence.
- Simulate a model change → additive backfill completes with retrieval served throughout.
- Kill the job mid-run → rerun converges; no row left with a hash/embedding mismatch.
- Data-quality validator fails a deliberately broken fixture.

## Definition of Done

- Stale data is detectable, flagged, and never asserted as current.
- Re-embedding is incremental, idempotent, and resumable.
- A model migration can be performed without downtime and without an in-place rebuild.
- Adding a destination is documented and gated by the CI validator.
- No provenance, licence, or attribution is lost by a refresh.

## Handoff

Report the schedule, TTL table as implemented, the run-log format, the model-migration runbook,
and the add-a-destination procedure. State what [Task 41](41-admin-knowledge-curation.md) must
call to trigger a targeted re-embed after a manual correction.

## Suggested branch and commit

- Branch: `agent/task-40-tkb-refresh-reembed`
- Commit: `feat: add TKB refresh and re-embedding pipeline`
