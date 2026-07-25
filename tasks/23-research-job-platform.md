# Task 23 — Research Job Platform

## Objective

Implement durable asynchronous research-job orchestration so long-running agents never block request threads.

## Dependencies

- Tasks 07, 18, and 22 complete.

## Required reading

- `plans/superpower/PLAN.md` C2 async execution and resilience sections
- `plans/USE-CASES.md` UC-C2-01, C2-02, T07
- `plans/BACKLOG.md` S4-2
- `docs/PLAN-COMPATIBILITY.md` research failure rule

## Scope

- `research_job` migration/domain/repository with `queued`, `running`, `completed`, and `failed`, progress, error code, attempts, timestamps, and research-run identity.
- Start-job application service requiring `BRIEF_COMPLETE`, user ownership, and no invalid duplicate active job.
- Background executor abstraction suitable for local single-node operation and replaceable later; persist state before dispatch.
- Poll/status API and frontend query hook.
- Bounded execution timeout consistent with plan, cancellation/shutdown behavior, restart recovery policy, retry/re-run semantics, and stale-running-job reconciliation.
- Failure writes `research_job.status=failed` and `error_code`; `trip.status` remains at its last valid value.
- Successful completion transition hook for later recommendation persistence and `RESEARCH_READY` update.
- Observability/request correlation across HTTP request and background work.

## Do not

- Do not implement the research agent or recommendations.
- Do not hold an HTTP connection for the full job.
- Do not invent a trip-level failed status.
- Do not depend on Redis/queue infrastructure unless a new ADR is approved.

## Validation

Test start/poll, invalid trip status, concurrent starts, timeout, executor failure, restart reconciliation, retry/re-run, ownership, and status-transition transaction boundaries.

## Definition of Done

- Jobs are durable and observable.
- HTTP start returns 202 with job ID.
- Failures are typed and leave Trip recoverable.
- Later research logic has a clear execution/persistence callback boundary.

## Handoff

Document executor model, state machine, timeout/retry policy, recovery behavior, and callback contract for Task 25.

## Suggested branch and commit

- Branch: `agent/task-23-research-jobs`
- Commit: `feat: add durable research job platform`
