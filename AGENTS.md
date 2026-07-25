# AI Agent Instructions — Travel Planner

> **Read this file first.** Every AI coding session on this repo follows
> [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md).

## Quick start

```bash
npm run prereq                    # must pass before any code generation
```

| Step | Action |
|---|---|
| 1 | Read task → map to feature **C1–C5** or **C6+**; check [`plans/USE-CASES.md`](plans/USE-CASES.md) for acceptance criteria |
| 2 | Read [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) sections for your layer |
| 3 | Follow workflow: **CONTRACT → DOMAIN → SERVICE → ADAPTERS → ROUTE → FRONTEND → VERIFY** |
| 4 | Self-check [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md) §7 before finishing |

## Non-negotiables

- **No business logic in controllers** — services only (`application/`)
- **No LangChain4j outside** `ai/langchain4j/`
- **No hand-written API types** on frontend — OpenAPI → codegen
- **No LLM/HTTP inside** `@Transactional` methods
- **≤3 params** per function · **≤120 chars** per line · **≤40 lines** per method
- **Stub adapter** when vendor API is undecided (`§4.0.7`)
- **Vertical slice** — new feature = new packages; don't edit unrelated services (§4.0.8)
- **Minimal diff** — only change what the task requires

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
| [`plans/USE-CASES.md`](plans/USE-CASES.md) | Use cases & acceptance criteria |
| [`docs/UI-UX-DESIGN-SYSTEM.md`](docs/UI-UX-DESIGN-SYSTEM.md) | **Any web or PWA UI work** |
| [`docs/ADDING-A-FEATURE.md`](docs/ADDING-A-FEATURE.md) | **Adding C6+ or new capabilities** |
| [`docs/ARCHITECTURE-DIAGRAMS.md`](docs/ARCHITECTURE-DIAGRAMS.md) | System, flow, and activity diagrams |
| [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md) | **Every code generation task** |
| [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) | Architecture, rules, NFRs, CI |
| [`plans/BACKLOG.md`](plans/BACKLOG.md) | Sprint stories, DoD |
| [`docs/adr/`](docs/adr/) | Locked decisions (Gradle, JWT, PWA, …) |

## Stop and ask the user when

- Task changes locked architecture (§1 of PLAN)
- Vendor API required but no stub exists and no ADR
- Task needs a new runtime service (Redis, queue, etc.) not in plan
- Prerequisite check fails (`npm run prereq`)
