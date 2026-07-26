# `src/generated/` — do not edit

Everything under this directory is produced by OpenAPI code generation and overwritten on every
`npm run codegen`. Hand edits are lost and, worse, cause frontend/backend contract drift — the
exact failure the generated-client rule exists to prevent (PLAN §4.2.2, §6).

## What is here

| Path | Produced by |
|---|---|
| `api/schema.d.ts` | `openapi-typescript` — `paths`, `components`, `operations` for the whole contract |

## The pipeline

| Item | Value |
|---|---|
| Contract source | `apps/backend/src/main/java/com/travelplanner/api/openapi/openapi.yaml` (PLAN §4.0.0.1) |
| Error registry | `apps/backend/src/main/java/com/travelplanner/api/openapi/errors.yaml` |
| Generator | [`openapi-typescript`](https://openapi-ts.dev) `^7.13.0` (devDependency of `apps/frontend`) |
| Command | `npm run codegen` from the repo root |
| Drift gate | The `frontend` CI job re-runs codegen and fails on `git diff --exit-code src/generated/` |

```bash
npm run codegen                              # repo root
git diff --exit-code apps/frontend/src/generated/
```

## Policy

- **Never hand-write an API type.** Alias the generated one:
  `type Trip = components['schemas']['Trip']`.
- **Never import `schema.d.ts` from a component.** Features go through `lib/api/<resource>-api.ts`,
  which goes through `lib/api/client.ts`.
- **Regenerate in the same commit as the spec change.** A stale client fails CI, not review.
- **`zod` schemas in `lib/api/schemas/` are validation, not duplication.** Each is annotated with
  its generated type (`z.ZodType<components['schemas']['X']>`), so a contract change this file does
  not follow is a compile error.

## The one codegen exception (ADR 007)

The two chat paths added by tasks 20/21 —
`POST /planner/chat/messages` and `POST /trips/{tripId}/chat/messages` — stream
`text/event-stream`. No generator emits a usable SSE client, so their event union is hand-authored
in `lib/api/chat-stream.ts` and mirrored by a backend sealed type, and those paths are excluded
from the codegen-drift gate.

They are marked `x-sse-stream: true` in the contract. `OpenApiSpecTest` fails the backend build if
a marked operation declares any content type other than `text/event-stream`, so the exception can
never quietly widen. Request and response *bodies* of every non-streaming chat endpoint stay
generated.
