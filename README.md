# Travel Planner

A **knowledge-based, LLM-powered travel planner**. Users chat to plan trips. Decisions are grounded in a **Travel Knowledge Base** — destinations, food, areas, POIs, seasonality, and prices — with the LLM reasoning over retrieved facts, not inventing them.

> **Status:** Planning complete — architecture, delivery structure, and sprint backlog are locked. Application code not yet scaffolded. Start with **Phase 0a** (Sprint 0) per [`plans/BACKLOG.md`](plans/BACKLOG.md).

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
| Frontend | Next.js 15 (App Router), TypeScript, Ant Design, Tailwind CSS, TanStack Query, next-intl |
| Backend | Java 21, Spring Boot 3.x, Gradle, LangChain4j |
| Database | PostgreSQL + pgvector | Travel Knowledge Base (TKB) + embeddings |
| AI | Anthropic + OpenAI (switchable at runtime) |
| Auth | Local password, **Gmail sign-up/login (Firebase)**, GitHub OAuth |
| Mailer | Resend (`resend.com`) |
| Runtime | Docker Compose |

## Repository layout (planned)

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

## Quick start (planned)

```bash
# 1. Check prerequisites
npm run prereq

# 2. Copy environment template (single .env at repo root for all services)
cp .env.example .env
# Edit .env with your API keys — never commit .env

# 3. Start full stack
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api/v1 |
| PostgreSQL | localhost:5432 |

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
- **API** — `/api/v1/`, GET/POST/PUT/DELETE, standard error `{ code, message, details }`
- **Vertical-slice extensibility** — add C6+ without editing existing features (§4.0.8)
- **Industry standards** — ArchUnit, JaCoCo, Micrometer, CSRF, virtual threads (§4.0.9)

## Development workflow

Every feature and AI-generated change follows the mandatory workflow in **§12** of the plan.
**AI agents:** read [`AGENTS.md`](AGENTS.md) → [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md) first.

```
PREREQ → PLAN → CONTRACT → DOMAIN → SERVICE → ADAPTERS → ROUTE → FRONTEND → VERIFY
```

See [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) for full coding rules, layer boundaries, and checklists.

## Documentation

| Document | Description |
|---|---|
| [`AGENTS.md`](AGENTS.md) | **AI agent entry point** — quick rules & doc index |
| [`docs/ADDING-A-FEATURE.md`](docs/ADDING-A-FEATURE.md) | How to add C6+ features (vertical slice) |
| [`docs/AI-AGENT-WORKFLOW.md`](docs/AI-AGENT-WORKFLOW.md) | **AI code generation workflow** — pipeline, gates, forbidden patterns |
| [`plans/USE-CASES.md`](plans/USE-CASES.md) | **Use case catalog** — acceptance criteria & MVP funnel |
| [`plans/TRAVEL-KNOWLEDGE-CATALOG.md`](plans/TRAVEL-KNOWLEDGE-CATALOG.md) | **TKB catalog** — PM review: timeline, routes, transport, apps |
| [`plans/superpower/PLAN.md`](plans/superpower/PLAN.md) | Master plan — architecture, rules, NFRs, CI/CD |
| [`plans/BACKLOG.md`](plans/BACKLOG.md) | Sprint-ready epics & stories (Sprints 0–8) |
| [`docs/ARCHITECTURE-DIAGRAMS.md`](docs/ARCHITECTURE-DIAGRAMS.md) | **Architecture diagrams** — system, flow, activity (Mermaid) |
| [`docs/adr/`](docs/adr/) | Architecture decision records (Gradle, JWT, extensibility, auth, Resend) |

## License

Private repository — all rights reserved.
