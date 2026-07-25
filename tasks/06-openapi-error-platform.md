# Task 06 — OpenAPI and Error Platform

## Objective

Create the versioned API contract, standard errors, pagination conventions, and TypeScript code-generation path used by all later features.

## Dependencies

- Tasks 02, 03, 04, and 05 complete.

## Required reading

- `plans/superpower/PLAN.md` §6 and §6.1
- **[`docs/adr/008-optimistic-concurrency.md`](../docs/adr/008-optimistic-concurrency.md) — Accepted; defines the `expected_version` convention and `version_conflict` this task must register**
- [`docs/adr/007-chat-streaming-transport.md`](../docs/adr/007-chat-streaming-transport.md) — the two chat paths are documented as `text/event-stream` and **excluded from the codegen-drift gate**
- `docs/AI-AGENT-WORKFLOW.md` contract step
- `plans/BACKLOG.md` S1-4, S1-5, S2-9, S2-10

## Scope

- OpenAPI source owned by the backend and versioned under `/api/v1/`.
- Health/readiness paths reflected in the spec.
- Standard error schema `{ code, message, details }` and initial error catalog.
- **`version_conflict` registered in the error catalog** with an i18n key — `409` plus `details.current_version` (ADR 008).
- **`expected_version` as a documented body-level convention** for mutations, not a header. It is chosen over `ETag`/`If-Match` precisely because it is expressible in OpenAPI and flows through codegen into the typed client; header-based concurrency would need hand-plumbed handling in the generated-client layer this project forbids editing.
- A missing `expected_version` on a versioned mutation is `400 validation_failed` — **never** treated as "force overwrite".
- Pagination schema using `page`, `page_size`, `sort`, and response metadata.
- Request ID propagation and response header documentation.
- OpenAPI validation in backend tests/CI.
- Generated TypeScript client/types placed only in `apps/frontend/src/generated/api/`.
- Root `npm run codegen` and a CI drift check.
- Thin frontend API-client boundary with credentials support and runtime response validation extension points.

## Do not

- Do not manually duplicate generated DTOs.
- Do not fully specify C1–C5 endpoints before their task unless needed for a stable shared primitive.
- Do not return JPA entities through the contract.
- Do not introduce GraphQL.

## Validation

```bash
npm run codegen
git diff --exit-code apps/frontend/src/generated/
cd apps/backend && ./gradlew test
cd apps/frontend && npm run typecheck && npm run build
```

## Definition of Done

- Backend spec validates.
- Generated client compiles.
- Drift is detected by CI.
- Error and pagination behavior is contract-tested.

## Handoff

Document the exact spec location, generator/version, generated-code policy, and error-code registration procedure.

## Suggested branch and commit

- Branch: `agent/task-06-api-contract`
- Commit: `feat: establish OpenAPI and error platform`
