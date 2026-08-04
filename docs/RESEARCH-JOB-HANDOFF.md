# Research job platform handoff notes (Task 23 → Task 24/25)

Task 23 adds the **durable asynchronous research-job platform**: the machinery that starts a C2
research run, runs it on a background worker within a bounded budget, records its outcome durably,
and drives `trip.status` — without ever holding an HTTP thread for the run. It deliberately contains
**no research agent and no recommendations**; those are task 25, and they plug in through the two
callback seams described below.

Everything lives in the vertical slice `application/research/`, with the domain in
`domain/model/ResearchJob.java` + `domain/enums/ResearchJobStatus.java`, the port in
`domain/port/ResearchJobRepositoryPort.java`, persistence in `infrastructure/persistence/`, and the
HTTP surface in `api/controller/ResearchController.java`.

## State machine

Two state machines move in lock-step: the **job** and the **trip**.

### Job (`ResearchJob`, `research_job.status`)

```
QUEUED ──beginRunning──▶ RUNNING ──complete──▶ COMPLETED
                           │
                           └────fail(code)────▶ FAILED
```

- Immutable record; every transition returns a new instance so the ADR 008 `@Version` lock stays
  trustworthy. Illegal moves (complete a queued job, fail a finished one, progress a non-running one)
  throw `validation_failed` rather than being absorbed — a background caller on a stale view is a bug
  to surface.
- Column invariants are mirrored in the constructor **and** the migration
  (`V24__create_research_job.sql`): `error_code` exactly on `FAILED`, `started_at` exactly once past
  `QUEUED`, `completed_at` exactly when terminal, `progress_pct` in 0–100.
- `research_run_id` equals `id` at creation (one row, one run). It exists as its own column so a run
  can be attributed to persisted recommendations (task 25) without assuming id == run.
- A **re-run is a new row** (UC-C2-07). `attempts` counts entries into `RUNNING` for one job; history
  is preserved by never mutating a terminal row.

### Trip (`ResearchStatusTransition`, `trip.status`)

```
BRIEF_COMPLETE ──start──▶ RESEARCH_QUEUED ──begin──▶ RESEARCH_RUNNING ──complete──▶ RESEARCH_READY
      ▲                          │                          │
      └────────fail/recover──────┴──────────────────────────┘
```

`ResearchStatusTransition` is a **sibling** of `TripStatusTransition`, not an extension of it. The
intake helper (task 18) owns `DRAFT`/`CLARIFICATION_NEEDED`/`BRIEF_COMPLETE` and refuses everything
else, so a client cannot skip the C2 gate by writing a research status directly. Research states its
own four moves next to the service that performs them.

**There is no trip-level failed status** (the task's Do-not list, `docs/PLAN-COMPATIBILITY.md`: "trip
keeps last valid status"). On failure the *job* is `FAILED` with an `error_code`, and the trip is
**recovered to `BRIEF_COMPLETE`** — the last state a re-run can start from — so a user is never
stranded in `RESEARCH_RUNNING` behind a dead job (UC-C2-07). This recovery-to-`BRIEF_COMPLETE` is the
deliberate reading of "recoverable" chosen for this task; document any change here if a later task
introduces a resume-in-place model.

## Executor model

`ResearchJobExecutor` (`void enqueue(UUID jobId)`) is the dispatch seam. The only implementation is
`LocalResearchJobExecutor` — a bounded in-process `ThreadPoolTaskExecutor` (`ResearchExecutionConfig`),
**no Redis, no broker** (the task's Do-not list). Replacing this file with a queue-backed executor is
the documented upgrade path; nothing calling `enqueue` would change.

- **Persist before dispatch.** `ResearchJobService.start` writes the `QUEUED` job and moves the trip
  to `RESEARCH_QUEUED` in one short `@TransactionalWrite`, then publishes `ResearchJobQueuedEvent`.
  `LocalResearchJobExecutor.onJobQueued` is a `@TransactionalEventListener(AFTER_COMMIT)`, so a worker
  is handed a job only once its row is durably committed. If the transaction rolls back, no dispatch
  happens. This also breaks the service↔executor dependency cycle.
- **Two pools, one budget.** The *worker* pool runs `ResearchJobRunner.run` (one slot per concurrent
  job). The *timeout* pool runs the `ResearchJobHandler` under `Future.get(timeout)` so the runner can
  `cancel(true)` and interrupt the handler thread on overrun — one pool cannot interrupt itself when
  full without deadlocking.
- **No transaction spans the handler.** The 90 s agent runs with no DB connection held (PLAN/AGENTS:
  no LLM/HTTP inside `@Transactional`). Durable transitions are separate proxied
  `@TransactionalWrite` calls on `ResearchJobService`.

## Timeout / retry policy

- Budget: `travelplanner.research.job-timeout-ms`, default **90 000 ms** (PLAN §14 p95). Also
  `worker-pool-size` (default 2) and `worker-queue-capacity` (default 100), all env-overridable.
- Outcomes written to `research_job.error_code`: `research_timeout` (overran budget → interrupted),
  `research_failed` (handler threw), `research_interrupted` (worker interrupted, e.g. shutdown). These
  are **job error codes, not API catalog codes** — they describe why a run ended, never an HTTP
  response, so they are intentionally absent from `errors.yaml`.
- Every lifecycle transition is **idempotent against a stale view**: `markCompleted`/`failJob` are
  no-ops unless the job is still `RUNNING`, so a timeout and a late completion cannot both act on one
  run. `beginRunning` returns `Optional.empty()` on a duplicate dispatch.

## Recovery behavior (restart / stale-running)

`ResearchStaleJobReconciler` is an `ApplicationRunner` (same boot point as `DevAdminSeeder`):

- Every `RUNNING` job at boot is **orphaned by definition on a single node** — its worker died with
  the previous process — so it is failed with `research_timeout` and its trip recovered to
  `BRIEF_COMPLETE`. (This is a strict superset of the task's "older than the timeout" rule; at boot
  they are all stale. A multi-node deployment would swap this for an age-filtered `@Scheduled` sweep.)
- Every `QUEUED` job is **re-enqueued** (committed but never dispatched, e.g. crash between commit and
  dispatch). It then runs exactly as a fresh start would.
- Idempotent: safe to run repeatedly.

## Observability / request correlation

`LocalResearchJobExecutor` captures the caller's MDC (the `X-Request-Id` from `RequestIdFilter`) at
dispatch and restores it on the worker thread, so a background run's log lines trace back to the HTTP
call that started it. Every transition logs `job`, `trip`, and `user` ids; failures also log the
`error_code`.

## Callback contract for Task 25

Two seams, both defaulted with `@ConditionalOnMissingBean` no-ops so the platform is testable and
demonstrable end-to-end today (`NoOpResearchJobHandler` completes immediately;
`NoOpResearchCompletionHook` does nothing). Task 25 registers real beans and neither of these files is
edited.

1. **`ResearchJobHandler.execute(ResearchJobContext)`** — *the work*. Runs on a background worker with
   **no transaction held**, so LLM/HTTP are permitted here. Contract:
   - return normally → platform marks the job `COMPLETED` and moves the trip to `RESEARCH_READY`
     (invoking the completion hook);
   - throw → platform marks the job `FAILED` (`research_failed`) and recovers the trip;
   - overrun the budget → platform interrupts the thread and marks `research_timeout`, **so a long
     call must honour `Thread.interrupt()`**.
   `ResearchJobContext` carries `jobId`, `researchRunId`, `tripId`, `userId`, and a
   `reportProgress(int)` sink (advisory 0–100, never ordering). A handler that persists results must do
   so in its **own** short `@TransactionalWrite` method.

2. **`ResearchCompletionHook.onResearchCompleted(jobId, tripId)`** — *atomic persistence seam*. Called
   **inside** the same transaction that marks the job `COMPLETED` and moves the trip to
   `RESEARCH_READY`. Task 25 may implement this to persist ranked recommendations in lock-step with the
   status move, so a client that sees `RESEARCH_READY` is guaranteed the recommendations exist — or may
   persist inside its handler and leave this a no-op. **Persistence only here — no LLM, no HTTP.**

Task 24 (deterministic destination ranking) and task 25 (research agent) are the consumers:
`research_run_id` is the attribution key for the `ranked_recommendation` rows they will write.

### The `start_research` chat tool is not implemented here

Task 22's `TRIP-CHAT-HANDOFF.md` describes an extension procedure for a `start_research` trip-chat
tool under `BRIEF_COMPLETE`. That is out of scope for task 23 (and belongs to task 22/27's tool
surface). When added, it should delegate to `ResearchJobService.start` exactly as the REST controller
does — same gate, same persist-before-dispatch, same 202 semantics.

## HTTP surface

- `POST /api/v1/trips/{tripId}/research/run` → **202 Accepted** `{ job_id, status: "queued", … }`;
  `400 validation_failed` when the trip is not `BRIEF_COMPLETE` or a job is already active;
  `404 not_found` when the trip is not the caller's.
- `GET /api/v1/trips/{tripId}/research/jobs/{jobId}` → `200` with the job; `404` when the job is not
  this trip's or the trip is not the caller's (indistinguishable — no probing for another user's data).
- Wire status is lower-case (`queued|running|completed|failed`); the domain/db keep UPPER_SNAKE and
  map at the DTO boundary (`ResearchJobResponse`).

Frontend: `lib/api/research-api.ts` (+ `research.schema.ts` zod), `features/research/` hook
(`use-research-job.ts`, polls while `queued`/`running`, stops on terminal and invalidates the trip
detail) and a minimal `ResearchJobPanel` on the trip detail screen (start control at
`BRIEF_COMPLETE`, live progress while active, done note at `RESEARCH_READY`; **no recommendations
UI**). Locales under `research`.
