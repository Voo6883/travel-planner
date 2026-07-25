# Task 15 — Architecture and Quality Gates

## Objective

Complete the automated engineering gates promised by the plan after the foundational code exists.

## Dependencies

- Tasks 02–14 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0.9, §12, §13, §15
- `docs/AI-AGENT-WORKFLOW.md` quality and forbidden-pattern sections
- `plans/BACKLOG.md` S1-9, S1-10, S2-12, S2-13

## Scope

### Backend gates

- Checkstyle line/naming rules.
- JaCoCo thresholds focused on domain/application code.
- ArchUnit rules for inward dependency direction, pure domain, no JPA leakage, no LangChain4j outside its adapter package, and controller/service boundaries.
- Flyway validation and Testcontainers integration suite.
- Transaction/rollback and optimistic-lock regression tests.

### Frontend gates

- ESLint strict TypeScript/no-explicit-any/max-len rules.
- Prettier consistency.
- Import-boundary rules for app/features/shared/generated layers.
- Generated API drift check.
- Unit/component test coverage baseline and production build.

### Platform extensibility

- `features/_template/`, `ToolRegistry`, feature flags, and query-key conventions only where required by the accepted plan.
- Actuator/Prometheus exposure with secure production defaults.

### CI

- Integrate all gates into required PR checks with useful failure output.
- Avoid live external-service dependencies.

## Do not

- Do not enforce subjective method/file size heuristics through brittle transformations.
- Do not weaken gates merely to make generated code pass.
- Do not add application features.

## Definition of Done

- Layer violations fail CI.
- Generated contract drift fails CI.
- Coverage thresholds are meaningful and documented.
- Full foundation build is reproducible locally and in CI.

## Handoff

Publish exact commands, thresholds, exclusions, and the process for justified architecture exceptions.

## Suggested branch and commit

- Branch: `agent/task-15-quality-gates`
- Commit: `ci: enforce architecture and quality gates`
