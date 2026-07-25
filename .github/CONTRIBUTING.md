# Contributing — branch, PR, and CI policy

Delivered by [Task 05](../tasks/05-ci-repository-workflow.md) (backlog S0-4, S0-5).
Authority: [`plans/superpower/PLAN.md`](../plans/superpower/PLAN.md) §12 and §15.

## No code without a task ID

`tasks/NN-*.md` is the only valid work order. A request, an idea in a plan section, or something
noticed while reading is **not** a work order ([`docs/AGENT-HARNESS.md`](../docs/AGENT-HARNESS.md) §2).

Before starting, confirm in [`tasks/STATUS.md`](../tasks/STATUS.md) that every dependency is
`done` and the task is not `blocked`.

## Branch naming

| Kind | Pattern |
|---|---|
| Task work | `agent/task-NN-short-desc` |
| Fix | `fix/short-desc` |
| Docs only | `docs/short-desc` |

One task per branch and per PR. Start from the latest accepted trunk.

> **Current deviation.** Implementation is landing directly on `dev` as one commit per feature,
> not one branch/PR per task. This is a deliberate decision recorded in
> [`tasks/EXECUTION-BASELINE.md`](../tasks/EXECUTION-BASELINE.md) §7 (B-2 supersession) and
> tracked as follow-up **F-8**. Either the docs or the practice should be reconciled — the point
> of recording it is that the divergence is visible rather than silent.

## What CI checks

Defined in [`.github/workflows/ci.yml`](workflows/ci.yml). Every PR gets all of it; no job needs
a provider secret, so CI runs on forks.

| Job | Checks |
|---|---|
| `prereq` | `npm run prereq:test` on Ubuntu **and** Windows; full `npm run prereq` on Ubuntu. Independently asserts Node 22 / Java 21 |
| `backend` | `./gradlew build` on Java 21; test reports uploaded (14 days) |
| `frontend` | `npm ci` → lint → typecheck → test → build on Node 22 |
| `docker` | Builds the same Dockerfiles used locally via `docker compose build`, starts the stack, waits for readiness, smoke-tests `health` / `ready` / frontend. Asserts `"database":"UP"` |
| `security` | Fails if `.env` or secret-shaped files are tracked, or if a provider key pattern appears in tracked content. `dependency-review` on PRs |

Superseded runs are cancelled automatically (`concurrency`).

### Not yet gated — deliberately

Coverage, ArchUnit boundaries, OpenAPI codegen drift, Flyway validate, and the AI eval harness
depend on foundations that do not exist yet. They are owned by
[Task 15](../tasks/15-quality-gates.md) and [Task 06](../tasks/06-openapi-error-platform.md).

**Extend the existing jobs — do not add parallel ones.** Where each belongs:

| Future gate | Extends | Owner |
|---|---|---|
| JaCoCo coverage threshold | `backend` | task 15 |
| ArchUnit layer tests | `backend` | task 15 |
| Checkstyle / Spotless (120-char) | `backend` | task 15 |
| OpenAPI validate + codegen drift | `frontend` + `backend` | task 06 |
| Flyway validate (Testcontainers) | `backend` | task 07 |
| Prompt eval harness on `ai/prompt/**` | new job, PLAN §15.3 | task 27 |
| Lighthouse / PWA installability | `frontend` | task 13 |

## Pull requests

Fill in [`pull_request_template.md`](pull_request_template.md) completely. The two sections that
are not optional:

- **Scope contract** — completed *before* the first edit
- **Evidence** — real pasted command output. Generated code alone never justifies "done"
  ([`docs/AGENT-HARNESS.md`](../docs/AGENT-HARNESS.md) §6)

A truthful partial report is correct. An optimistic one corrupts every downstream task.

## Local commands

```bash
npm run prereq        # runtime gate — run before anything else
npm run prereq:test   # unit tests for the gate itself
npm run docker:up     # full stack
npm run wait          # block until the backend reports ready
npm run docker:down   # stop, keep data
npm run docker:reset  # stop and wipe the database volume
npm run dev:db        # Postgres only, for host-run hybrid development
```

Per app:

```bash
cd apps/backend  && ./gradlew build
cd apps/frontend && npm ci && npm run lint && npm run typecheck && npm run test && npm run build
```

## Secrets

All configuration lives in a single root `.env`, copied from `.env.example` (PLAN §4.0.0.2).
Never commit `.env`, never add a second env file, never bake a secret into an image. CI seeds its
own `.env` from `.env.example`, which also means **`.env.example` must stay in sync with what
Compose requires** — if it drifts, the `docker` job fails.
