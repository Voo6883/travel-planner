# AI Agent Instructions — Travel Planner

> **Read this file first**, then [`docs/AGENT-HARNESS.md`](docs/AGENT-HARNESS.md) (scope & drift
> control), then your task brief, then [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md).

## Quick start

```bash
npm run prereq                    # must pass before any code generation
```

**No code without a task ID.** Work orders live in [`tasks/`](tasks/), executed in dependency order.
Start at [`tasks/README.md`](tasks/README.md); check the live gate with `npm run task:context -- NN`.

### Branch policy

```
agent/task-NN-<slug>  ──PR──▶  dev  ──PR──▶  master
```

- **One task, one branch, one PR into `dev`.** Cut it from `dev`, not from `master`.
- `dev → master` is a milestone PR, not a per-task one.
- Direct commits to `dev` are for documentation and tooling only.

This is not bureaucracy, and it was not being followed: tasks 16–20 landed as direct commits to
`dev`, and the 2026-07-29 review named four things that cost. A task-sized PR is the only place a
reviewable diff exists; a task-sized branch is the only thing a bad task can be rolled back to; a
CI run bound to a PR is the only evidence that a *specific* task passed rather than the branch it
happened to be sitting on; and two agents working without branch isolation cannot run in parallel
at all. Committing straight to `dev` trades all four for one saved command.

| Step | Action |
|---|---|
| 0 | Read [`docs/AGENT-HARNESS.md`](docs/AGENT-HARNESS.md) — topic boundary, conflict protocol, drift tripwires |
| 1 | Open your brief `tasks/NN-*.md` → map to **C1–C5**; check [`plans/USE-CASES.md`](plans/USE-CASES.md) for acceptance criteria |
| 2 | Read [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) sections for your layer |
| 3 | Follow workflow: **CONTRACT → DOMAIN → SERVICE → ADAPTERS → ROUTE → FRONTEND → VERIFY** |
| 4 | Self-check [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md) §7 + the harness evidence gate before finishing |

**When docs conflict or a contract is unspecified: stop and ask.** Never resolve it by guessing —
see [`docs/AGENT-HARNESS.md`](docs/AGENT-HARNESS.md) §3.

## Non-negotiables

- **No business logic in controllers** — services only (`application/`)
- **No LangChain4j outside** `ai/langchain4j/`
- **No hand-written API types** on frontend — OpenAPI → codegen
- **No LLM/HTTP inside** `@Transactional` methods
- **≤3 params** per function · **≤120 chars** per line · **≤40 lines** per method
- **Stub adapter** when vendor API is undecided (`§4.0.7`)
- **Vertical slice** — new feature = new packages; don't edit unrelated services (§4.0.8)
- **Minimal diff** — only change what the task requires
- **Fewest comments that work** — default none. Write one only where a competent reader would
  otherwise make a *specific* mistake, and name it. Type doc is 1–2 sentences. Trade-offs, rejected
  alternatives and defect stories go in the commit message, an ADR, or a `tasks/STATUS.md` finding —
  all read on demand; a comment is read on every pass. `docs/AI-AGENT-WORKFLOW.md` §9.1

## Where code goes

| Task type | Package / folder |
|---|---|
| REST endpoint | `api/controller/` → delegate to `application/<feature>/` |
| Business rules | `application/<feature>/*Service.java` |
| Domain model | `domain/model/`, `domain/valueobject/` |
| Port interface | `domain/port/` |
| Identity / mail | `domain/port/IdentityProviderPort`, `MailerPort` → `infrastructure/auth/`, `infrastructure/mail/` |
| DB / external API | `infrastructure/` |
| LLM / agent | `ai/agent/`, `ai/langchain4j/` |
| Auth UI | `features/auth/` — local, Firebase Google, GitHub |
| Frontend screen | `features/<feature>/` — page stays in `app/` only |
| API client (FE) | `lib/api/<resource>-api.ts` |

## Docs index

| Document | When to read |
|---|---|
| [`docs/AGENT-HARNESS.md`](docs/AGENT-HARNESS.md) | **Every session, before coding** — scope, conflicts, drift, evidence |
| [`tasks/README.md`](tasks/README.md) | **Every session** — task index, authority order, execution rules |
| [`plans/USE-CASES.md`](plans/USE-CASES.md) | Use cases & acceptance criteria |
| [`docs/UI-UX-DESIGN-SYSTEM.md`](docs/UI-UX-DESIGN-SYSTEM.md) | **Any web or PWA UI work** |
| [`docs/ADDING-A-FEATURE.md`](docs/ADDING-A-FEATURE.md) | **Adding C6+ or new capabilities** |
| [`docs/ARCHITECTURE-DIAGRAMS.md`](docs/ARCHITECTURE-DIAGRAMS.md) | System, flow, and activity diagrams |
| [`docs/PLAN-COMPATIBILITY.md`](docs/PLAN-COMPATIBILITY.md) | Post-merge plan compatibility review |
| [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md) | **Every code generation task** |
| [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) | Architecture, rules, NFRs, CI |
| [`plans/BACKLOG.md`](plans/BACKLOG.md) | Sprint stories, DoD |
| [`docs/adr/`](docs/adr/) | Locked decisions (Gradle, JWT, PWA, …) |

## Stop and ask the user when

- Task changes locked architecture (§1 of PLAN)
- Vendor API required but no stub exists and no ADR
- Task needs a new runtime service (Redis, queue, etc.) not in plan
- Prerequisite check fails (`npm run prereq`)

## Cursor Cloud specific instructions

### Repository status: Phase 0 complete, Phase 1 in progress

This is a working application, not a planning repository. Anything that tells you otherwise is
out of date — report it rather than acting on it.

| | |
|---|---|
| Backend | `apps/backend` — Spring Boot 3.5 / Java 21 / Gradle, ~20 domain ports, Flyway migrations |
| Frontend | `apps/frontend` — Next.js 15 / React 19 / TypeScript, PWA, `en` + `ms` locales |
| Runtime | `docker-compose.yml` (full stack) and `docker-compose.dev.yml` (Postgres only) |
| CI | `.github/workflows/ci.yml` — five jobs, all gating |
| Done | Tasks 00–16 |
| In progress | Tasks 17–20 |
| Next | Task 21, and only after 17–20 close — see [`docs/HANDOFF-REMAINING-WORK.md`](docs/HANDOFF-REMAINING-WORK.md) |

**Do not scaffold anything.** If a task reads as "create the backend", the task is stale, not the
tree. Live status is [`tasks/STATUS.md`](tasks/STATUS.md); it is the only source of truth for what
is `done`, and prose anywhere else that disagrees with it is wrong by definition.

### Orient yourself in one command, not by reading the plan

```bash
npm run task:context -- 21      # gate, objective, DoD, resolved PLAN line ranges, open findings
```

`docs/generated/CODE-MAP.json` holds the rest: every port and its implementations, every error code
and whether the UI translates it, every migration and the next free version, every feature's files.
Read that and three or four real files. Reading `plans/superpower/PLAN.md` end to end costs about
3,700 lines and answers less.

### Commands that actually work here

```bash
npm run prereq                                   # toolchain gate
cp .env.example .env                             # ports are env-driven; 8081 locally

npm run dev:db                                   # Postgres + pgvector only
npm run dev                                      # both apps on the host, hot reload
docker compose up --build                        # full stack

npm run verify:fast                              # the edit loop — scoped to what you changed
npm run verify:task -- 18                        # before claiming a task is done
npm run verify:full                              # what CI runs (needs Docker)
npm run test:integration                         # the container suite on a LOCAL Postgres instead
npm run seed:validate                            # the knowledge seed, against the domain it becomes
```

Frontend on `:3000`, backend API on `:8080/api/v1` inside Docker (`:8081` on the host by default —
Oracle XE owns 8080 on some machines). Dev admin seed: `ADMIN` / `123456`, **dev/docker/local only**;
the `prod` profile refuses to construct the seeder, the mail stand-ins, the identity stand-ins, and
the stub LLM.

### Preinstalled toolchain (already satisfies the plan)

The base VM already provides the runtimes the plan requires — do **not** reinstall them:

- Node.js 22.x (`node -v`) — matches frontend requirement.
- JDK 21 (`java -version`) — matches backend requirement.
- npm 10.x, git 2.x.

Docker + Compose is **not** preinstalled. Without it, `npm run test:integration` runs the
Testcontainers suite against a locally-installed PostgreSQL instead — so the only thing genuinely out
of reach is the compose stack and its smoke test. Say which one you ran rather than reporting a bare
pass: "integration suite green on local Postgres 16.6" and "green in a pinned container" are
different claims.
