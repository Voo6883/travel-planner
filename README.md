# Travel Planner

A **knowledge-based, LLM-powered travel planner**. Users chat to plan trips. Decisions are grounded in a **Travel Knowledge Base** — destinations, food, areas, POIs, seasonality, and prices — with the LLM reasoning over retrieved facts, not inventing them.

> **Status: Phase 0 complete, Phase 1 in progress.** Tasks 00–16 are `done`; tasks 17–20 are in
> progress. Both applications build, both are gated by CI, and the stack runs. Live status is
> [`tasks/STATUS.md`](tasks/STATUS.md) — the only source of truth for what is `done`.
>
> **Start here:** `npm run task:context -- NN` for the task you are picking up, and
> [`docs/HANDOFF-REMAINING-WORK.md`](docs/HANDOFF-REMAINING-WORK.md) for what is left.
>
> Current schema head: migration `V20`; next free version `V21` (`npm run code-map` derives it).

## Features (v1)

| # | Feature | Description |
|---|---|---|
| C1 | **Intake** | Chat (primary) or form → structured `TripBrief` |
| C2 | **Research** | Knowledge-based ranking — TKB + RAG → grounded destination guides |
| C3 | **Itinerary** | Day timeline + travel routes + transport mode + app suggestions per leg |
| C4 | **Booking** | Search + book flights/hotels — human-confirmed |
| C5 | **Trip chat** | LLM-driven create + decisions across the full planning flow |

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 15 (App Router), TypeScript, Ant Design, Tailwind CSS, TanStack Query, next-intl, **PWA (Serwist)** |
| Backend | Java 21, Spring Boot 3.x, Gradle, LangChain4j |
| Database | PostgreSQL + pgvector | Travel Knowledge Base (TKB) + embeddings |
| AI | Anthropic + OpenAI (switchable at runtime) |
| Auth | Local password, **Gmail sign-up/login (Firebase)**, GitHub OAuth |
| Mailer | Resend (`resend.com`) |
| Runtime | Docker Compose |

## Repository layout

**Monorepo with separate app folders** — frontend and backend never mixed.

```
travel-planner/
├── apps/
│   ├── frontend/          # Next.js only — Node 22
│   └── backend/           # Spring Boot only — Java 21
├── docker/                # Dockerfiles (one per app)
├── scripts/               # check-prerequisites, wait-for-services
├── plans/                 # Architecture & feature plans
├── docker-compose.yml
└── package.json           # root orchestration scripts only
```

| Folder | Stack | Rule |
|---|---|---|
| `apps/frontend/` | Next.js, TypeScript, Ant Design | No Java/backend code |
| `apps/backend/` | Spring Boot, Java 21, Gradle | No React/frontend code |

Apps communicate via **`/api/v1/`** + OpenAPI codegen only. See [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) §4.0.

## Prerequisites

Run before any development:

```bash
npm run prereq
# or
./scripts/check-prerequisites.sh      # Unix / Git Bash
./scripts/check-prerequisites.ps1     # Windows PowerShell
```

| Tool | Version |
|---|---|
| Node.js | 22.x |
| Java JDK | 21 |
| Docker + Compose | v2+ |
| Git | 2.x+ |
| Gradle | 8.x (wrapper in repo — no global install required) |

## Quick start

```bash
# 1. Check prerequisites
npm run prereq

# 2. Copy environment template (single .env at repo root for all services)
cp .env.example .env
# Edit .env with your API keys — never commit .env

# 3. Start full stack
docker compose up --build
```

Or run the apps on the host with hot reload and only Postgres in Docker:

```bash
npm run dev:db                 # Postgres 16 + pgvector
npm run dev                    # backend + frontend, both hot-reloading
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api/v1 (host default `8081` — see `.env.example`) |
| PostgreSQL | localhost:5432 |

### Verifying a change

Three stages, cheapest first. The gates are identical — only the timing differs.

```bash
npm run verify:fast            # the edit loop; scoped to what git says you changed
npm run verify:task -- 18      # before claiming a task is done: coverage, ArchUnit, drift
npm run verify:full            # what CI runs, Testcontainers included (needs Docker)

npm run test:integration       # the Testcontainers suite against a LOCAL Postgres — no Docker.
                               # Creates a throwaway database per run and drops it afterwards.
```

### Working on a task

```bash
npm run task:context -- 21     # dependency gate, DoD, resolved PLAN line ranges, open findings
npm run code-map               # regenerate docs/generated/CODE-MAP.json
npm run task:report -- 18      # capture real command output as completion evidence
```

### Dev admin account (local / Docker only)

| Field | Value |
|---|---|
| Username | `ADMIN` |
| Password | `123456` |

> Do not use these credentials in production.

## Architecture highlights

- **Monorepo** — frontend + backend, OpenAPI as single contract source
- **Clean/hexagonal backend** — Controller routes only; Service owns business logic
- **Feature-based frontend** — `features/` modules aligned to C1–C5
- **Tailwind + Ant Design** — unified design tokens; Tailwind overrides Ant by default
- **Gmail sign-up & login** — Firebase Google; one button on login and register pages
- **Resend mailer** — welcome, verify, password-reset emails
- **PWA** — installable Next.js app via Serwist (manifest + service worker; §4.2.11)
- **API** — `/api/v1/`, GET/POST/PUT/DELETE, standard error `{ code, message, details }`
- **Vertical-slice extensibility** — add C6+ without editing existing features (§4.0.8)
- **Industry standards** — ArchUnit, JaCoCo, Micrometer, CSRF, virtual threads (§4.0.9)

## Development workflow

Every feature and AI-generated change follows the mandatory workflow in **§12** of the plan.

```
PREREQ → PLAN → CONTRACT → DOMAIN → SERVICE → ADAPTERS → ROUTE → FRONTEND → VERIFY
```

**AI agents — read in this order:**

```
AGENTS.md → docs/AGENT-HARNESS.md → tasks/NN-*.md → docs/AI-AGENT-WORKFLOW.md
```

Work is executed **one task per branch/PR** (`agent/task-NN-*`), in dependency order, against the
task briefs in [`tasks/`](tasks/). The [agent harness](docs/AGENT-HARNESS.md) defines scope
boundaries, the documentation-conflict protocol, and the evidence gate for completion.

See [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) for full coding rules, layer boundaries, and checklists.

## Documentation

| Document | Description |
|---|---|
| [`AGENTS.md`](AGENTS.md) | **AI agent entry point** — quick rules & doc index |
| [`tasks/README.md`](tasks/README.md) | **Implementation task plan** — 40 execution-sized briefs, authority order, execution rules |
| [`docs/AGENT-HARNESS.md`](docs/AGENT-HARNESS.md) | **Agent scope control** — topic boundary, conflict protocol, drift tripwires, evidence gate |
| [`docs/ADDING-A-FEATURE.md`](docs/ADDING-A-FEATURE.md) | How to add C6+ features (vertical slice) |
| [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md) | **AI code generation workflow** — pipeline, gates, forbidden patterns |
| [`plans/USE-CASES.md`](plans/USE-CASES.md) | **Use case catalog** — acceptance criteria & MVP funnel |
| [`plans/TRAVEL-KNOWLEDGE-CATALOG.md`](plans/TRAVEL-KNOWLEDGE-CATALOG.md) | **TKB catalog** — PM review: timeline, routes, transport, apps |
| [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) | Master plan — architecture, rules, NFRs, CI/CD |
| [`plans/BACKLOG.md`](plans/BACKLOG.md) | Sprint-ready epics & stories (Sprints 0–8) |
| [`docs/UI-UX-DESIGN-SYSTEM.md`](docs/UI-UX-DESIGN-SYSTEM.md) | **UI/UX design system** — tokens, layouts, PWA presentation |
| [`docs/ARCHITECTURE-DIAGRAMS.md`](docs/ARCHITECTURE-DIAGRAMS.md) | **Architecture diagrams** — system, flow, activity (Mermaid) |
| [`docs/PLAN-COMPATIBILITY.md`](docs/PLAN-COMPATIBILITY.md) | **Post-merge plan compatibility review** |
| [`docs/adr/`](docs/adr/) | Architecture decision records (001–010) — Gradle, JWT, extensibility, auth/Resend, PWA, **same-origin proxy, chat streaming, concurrency, session revocation, TKB sourcing** |

## License

Private repository — all rights reserved.
