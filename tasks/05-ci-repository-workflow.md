# Task 05 — CI and Repository Workflow

## Objective

Establish the Phase 0A continuous-integration skeleton and review workflow before feature work begins.

## Dependencies

- Tasks 01–04 complete.

## Required reading

- `plans/BACKLOG.md` S0-4 and S0-5
- `plans/superpower/PLAN.md` §12 and §15
- `docs/AI-AGENT-WORKFLOW.md`

## Scope

- GitHub Actions jobs for root prerequisite checks, backend test/build, frontend lint/test/build, and Docker image builds.
- Explicit Node 22 and Java 21 setup and version assertions.
- Dependency caching without caching secrets or generated stale contracts.
- Pull-request template containing the project checklist, task ID, validation commands, migration notes, stub/live integration declaration, and screenshots when UI changes.
- Concurrency cancellation for superseded PR runs.
- Basic secret scanning and dependency review where available.
- Required artifact/log retention sufficient to diagnose failures.
- Documentation of branch naming and one-task-per-PR policy.

## Do not

- Do not add code coverage or architecture gates that depend on foundations not yet implemented; Task 15 will complete those.
- Do not make CI depend on live AI, mail, OAuth, payment, flight, or hotel credentials.

## Validation

- Open a test PR or run workflow dispatch if supported.
- Confirm a wrong Node/Java version or deliberate test failure causes CI failure.
- Confirm Docker builds use the same Dockerfiles as local development.

## Definition of Done

- Every PR receives deterministic backend, frontend, and container feedback.
- CI runs without provider secrets.
- PR template captures required implementation evidence.
- Phase 0A exit criteria are met.

## Handoff

List checks that later tasks must extend rather than duplicate.

## Suggested branch and commit

- Branch: `agent/task-05-ci-workflow`
- Commit: `ci: add foundation workflows and PR template`
