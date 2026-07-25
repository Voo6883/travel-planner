# Task 02 — Backend Minimal Scaffold

## Objective

Create the smallest valid Java 21 / Spring Boot 3 backend that establishes the planned package boundary and health surface.

## Dependencies

- Task 01 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0, §4.0.1, §4.0.2, §6.1
- `plans/BACKLOG.md` S1-1 and the health portions of S0-2/S0-3
- `docs/AI-AGENT-WORKFLOW.md`

## Scope

Under `apps/backend/`:

- Gradle Kotlin DSL project and committed Gradle wrapper.
- Java 21 toolchain.
- Spring Boot application entry point.
- Initial packages for `domain`, `application`, `api`, `infrastructure`, `ai`, and `config` without premature business classes.
- Profiles for local/dev, Docker, test, and production configuration placeholders.
- `GET /api/v1/health` liveness endpoint independent of the database.
- `GET /api/v1/ready` readiness endpoint with a replaceable readiness contributor; database wiring may be completed in Task 04/07.
- Basic error handling for unexpected failures without leaking stack traces.
- Unit/slice tests for health endpoints.
- Formatting and build scripts needed by later CI.

## Do not

- Do not implement authentication, Trip, JPA entities, Flyway, LangChain4j, or external providers.
- Do not embed credentials or a production datasource.
- Do not collapse planned layers into a single package.

## Validation

From `apps/backend`:

```bash
./gradlew clean test
./gradlew bootRun
```

Verify `/api/v1/health` returns 200. Document the temporary readiness behavior until PostgreSQL is wired.

## Definition of Done

- Backend compiles on Java 21.
- Tests pass.
- Health route is stable under `/api/v1/`.
- Package direction is ready for ArchUnit enforcement later.
- No business feature has been implemented early.

## Handoff

Document application name, ports, profiles, Gradle tasks, and readiness extension point.

## Suggested branch and commit

- Branch: `agent/task-02-backend-scaffold`
- Commit: `build: scaffold Spring Boot backend`
