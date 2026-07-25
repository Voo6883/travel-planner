# Task 38 — Full-System Verification

## Objective

Verify the complete C1–C5 system across contracts, services, browser flows, containers, failures, and security boundaries before release configuration.

## Dependencies

- Tasks 01–37 complete or explicitly waived with documented rationale.

## Required reading

- All acceptance criteria in `plans/USE-CASES.md`
- Phase exit criteria in `plans/superpower/PLAN.md`
- `plans/BACKLOG.md` Definition of Done
- `docs/PLAN-COMPATIBILITY.md`

## Scope

### Automated suites

- Clean-checkout prerequisite and Docker startup.
- Backend unit, architecture, contract, migration, Testcontainers, security, and adapter tests.
- Frontend lint, typecheck, unit/component, accessibility smoke, and production build.
- OpenAPI generated-client drift.
- Playwright E2E for:
  - Local register/verify/login/logout/account lifecycle.
  - Firebase/GitHub via deterministic provider fixtures.
  - Admin authorization/audit.
  - Planner chat → Trip → clarification → `BRIEF_COMPLETE`.
  - Research job → ranked sourced results → destination selection.
  - Itinerary generation → timeline/routes/apps → chat refinement.
  - Stub booking search → explicit idempotent confirmation.
  - Reload/resume/restart persistence.
  - PWA install/offline shell/API network-only.

### Failure and isolation matrix

- Provider timeout/malformed AI output/tool failure.
- Missing/low-confidence KB data.
- Research timeout/retry/restart.
- Impossible itinerary/missing route/concurrent edit.
- Expired/changed quote, duplicate confirmation, payment ambiguity.
- Redis unavailable.
- Cross-user access attempts for every aggregate.
- XSS/prompt/tool-injection/rate-limit cases.

### Evidence

- Test results, coverage, build artifacts, dependency/secret scans, performance baseline, AI cost/latency/eval summary, and known limitations.

## Do not

- Do not fix failures by weakening acceptance/security rules.
- Do not call uncontrolled live providers in CI.
- Do not mark release-ready with skipped critical paths.

## Definition of Done

- Full deterministic stack passes from a clean checkout.
- All P0 use cases and accepted Phase 2 stub flows have evidence.
- Failures are recoverable and user isolation is proven.
- Remaining issues are classified with owner/severity/release impact.

## Handoff

Produce a verification report and exact blockers/inputs for Task 39.

## Suggested branch and commit

- Branch: `agent/task-38-system-verification`
- Commit: `test: verify full Travel Planner system`
