# Task 41 — Admin Knowledge Curation

## Objective

Let an administrator correct Travel Knowledge Base content through the application, with
provenance and an audit trail.

> **Why this task exists.** [ADR 010](../docs/adr/010-tkb-data-sourcing-embeddings.md) §7 requires
> admin CRUD over `destination_guide`, `poi`, and `travel_app`: *"Without it, correcting a closed
> restaurant requires a Flyway migration and a redeploy — an operational dead end for a knowledge
> product."* Its Consequences section notes the task was not covered. Tasks 00–39 predate the
> ADR. Recorded as blocker **B-4** in [`EXECUTION-BASELINE.md`](EXECUTION-BASELINE.md).

## Dependencies

- Tasks 16 and 17 complete.
- Task 12 admin platform complete — reuses its role model, guard, and audit pattern.
- Task 40 complete — curation triggers targeted re-embedding.

## Required reading

- [`docs/adr/010-tkb-data-sourcing-embeddings.md`](../docs/adr/010-tkb-data-sourcing-embeddings.md) §2, §4 and §7 — authoritative for this task
- [`docs/adr/008-optimistic-concurrency.md`](../docs/adr/008-optimistic-concurrency.md) — editing rules
- `plans/superpower/PLAN.md` §4.0.6 admin role and audit
- `docs/UI-UX-DESIGN-SYSTEM.md` — any UI here follows it
- `tasks/12-admin-platform.md` — the pattern to extend, not duplicate

## Scope

### Backend

- Admin-only CRUD over `destination_guide`, `poi`, and `travel_app`.
- Every create/update requires a `knowledge_source` reference with `licence` and `attribution_text` — **a factual field may not be saved without provenance.**
- `coverage_level` transitions (`NONE` → `PARTIAL` → `FULL`) gated by the Task 40 data-quality validator; an admin cannot mark a destination `FULL` by assertion.
- Optimistic concurrency per ADR 008: `expected_version` in the body, `409 version_conflict` on mismatch.
- Editing content that participates in retrieval enqueues a targeted re-embed (Task 40) rather than embedding inline — embedding is an external call and must not sit inside a transaction.
- Correcting a stale row clears `stale` and refreshes `retrieved_at`.
- Soft-delete/deactivate for POIs that have closed, preserving referential integrity with existing itineraries.
- Every mutation writes an `audit_event`: actor, target, action, before/after, time, result.

### Frontend

- Admin-only screens under the existing `(admin)` shell and role guard from Task 12.
- Search/filter by destination, entity type, coverage level, and staleness.
- Editor showing current provenance, licence, and freshness alongside the content being changed.
- Explicit confirmation for coverage-level changes and deactivation.
- Complete loading, error, empty, offline, and conflict states; English and Malay strings.

### Tests

- Authorization: anonymous and `USER` are refused; only `ADMIN` succeeds.
- A save without provenance is rejected.
- `coverage_level` cannot reach `FULL` while the validator fails.
- Concurrent edit produces `409 version_conflict` and loses no data.
- Audit rows are written and roll back with their transaction.
- Re-embed is enqueued, not executed inline.

## Do not

- Do not let an admin author content with an LLM.
- Do not allow a factual field to be saved without a `knowledge_source`.
- Do not grant curation rights to `USER`.
- Do not hard-delete POIs referenced by an itinerary.
- Do not embed inside a transaction or call the embedding provider from a controller.
- Do not duplicate the Task 12 admin shell, guard, or audit infrastructure — extend it.
- Do not build the refresh scheduler here; [Task 40](40-tkb-refresh-reembed.md) owns it.

## Validation

- Edit a POI's opening hours → row updated, `stale` cleared, audit row written, re-embed enqueued.
- Attempt a save with no source → rejected with a typed error.
- Attempt `FULL` coverage on a destination failing the validator → refused with the reason.
- Two concurrent edits → second receives `409` with `current_version`.
- `USER` and anonymous requests → `403`.
- Deactivate a POI used by an existing itinerary → itinerary still renders.

## Definition of Done

- An administrator can correct knowledge without a migration or redeploy.
- Provenance, licence, and attribution survive every edit.
- Coverage level cannot be raised past what the data supports.
- Every change is audited and attributable to a named actor.
- Retrieval reflects corrections once the enqueued re-embed completes.

## Handoff

Report the admin endpoints, the audit event shape, coverage-transition rules, and the re-embed
trigger contract shared with [Task 40](40-tkb-refresh-reembed.md).

## Suggested branch and commit

- Branch: `agent/task-41-admin-knowledge-curation`
- Commit: `feat: add admin knowledge curation`
