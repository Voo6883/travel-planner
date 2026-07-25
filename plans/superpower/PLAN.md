# Travel Planner — AI/LLM Integration Plan

> An **LLM-powered travel planner**. The user states where they might want to go,
> their budget, dates, and preferences. The system **researches online + uses
> historical data**, **decides** on destinations/plans, **suggests actions**, and
> lets the user perform **quick in-app actions to book flights and hotels**.

> **Status: PLAN — core features, system base, and delivery structure are LOCKED (§1, §3, §4, §6.1, §10, §12, §13–§16).**
> **Before coding:** run prerequisite check (§4.0.0). **Runtime:** Docker Compose (§4.0.0).
> **Kickoff gate:** Phase 0a starts when this plan is merged to `master` (sign-off = merge approval).
> Sprint-ready tasks live in [`plans/BACKLOG.md`](../BACKLOG.md).

---

## 1. Locked decisions

| Area | Decision |
|---|---|
| **Repository layout** | **Monorepo** — single repo for frontend + backend (§4.0) |
| Backend language | **Java 21** (records, sealed types, pattern matching, virtual threads) |
| Backend runtime | **JDK 21** — enforced via toolchain / CI (see §4.0) |
| Backend framework | **Spring Boot 3.x** |
| **AI framework** | **LangChain4j** (chosen — wrapped behind our own interfaces so it stays isolated) |
| **LLM providers** | **Anthropic + OpenAI, switchable** at runtime via config (§5.4) |
| Frontend | **Next.js (App Router) + TypeScript + Ant Design** (strictly typed, contracts generated from backend) |
| Frontend runtime | **Node.js 22** — enforced via `.nvmrc` / `engines` / CI (see §4.0) |
| Frontend structure | **Option A — feature-based modules** aligned to C1–C5 (§4.2) — **LOCKED** |
| Frontend layering | **Page = routing only; Feature = UI logic; API = generated client** (§4.2.6) |
| Frontend styling | **Tailwind CSS + Ant Design** — Tailwind overrides Ant; unified design tokens (§4.2.9) |
| Database | **PostgreSQL** (+ pgvector for embeddings) |
| Typing discipline | **Strict everywhere** — typed domain model, validated DTOs, OpenAPI→TS codegen, JSON-schema-bound LLM output (§6) |
| Architecture | **OOP + clean/hexagonal layering** (§4) |
| Backend layering | **Controller = routing only; Service = business logic** (§4.0.1, §12) |
| **Algorithms** | **Use DSA when measured need or clear correctness goal** — pure code in `domain/algorithm/` (§4.0.3) |
| **Function params** | **≤3 per function** — group extras into Query/Command/Context DTOs (§4.0.4) |
| **Naming** | **camelCase** functions · **kebab-case** files & URLs · **snake_case** i18n files (§4.0.4) |
| **i18n** | **next-intl** — required from v1; no hardcoded user-facing strings (§4.2.10) |
| **Auth** | **User-based accounts** — one user owns their trips; **no multi-tenant** (§4.0.5) |
| **Auth mechanism** | **Self-issued JWT** in **httpOnly cookie** — v1; OAuth add-on post-v1 (§4.0.5) |
| **Build tool** | **Gradle** (Kotlin DSL) — Java 21 toolchain; wrapper committed (§4.0) |
| **External adapters** | **Port + stub first** — real vendor APIs wired when chosen; never block features (§4.0.7) |
| **Admin** | **Seeded ADMIN account** — manage users, reset passwords (§4.0.6) — dev/docker only |
| **Runtime** | **Docker Compose** — full stack runnable in containers (§4.0.0) |
| **Pre-dev gate** | **Check prerequisites** before coding — install if missing (§4.0.0) |
| **API** | Versioned **`/api/v1/`** · CRUD (**GET/POST/PUT/DELETE** — no PATCH) · standard error envelope (§6.1) |
| **Annotations** | **Allowed** — Spring/Jakarta validation on backend; typed interfaces + zod on frontend (§4.0.2) |
| Money | `Money` value object (`BigDecimal` + `Currency`) — **never** float/double |

---

## 2. Product vision & core user flow

```
1. INTAKE      user states: candidate destinations (or "surprise me"),
               dates/flexibility, budget, party size, interests, constraints
                 │
2. RESEARCH     agent gathers: live online search + historical data
               (price trends, seasonality, weather, crowd levels)
                 │
3. DECIDE       score candidates vs budget + preferences → ranked
               recommendations, each with rationale + est. cost
                 │
4. SUGGEST      build a concrete itinerary + suggested flights/hotels
                 │
5. QUICK ACTION user confirms → in-app booking of flights / hotels
               (human-in-the-loop; payment via provider; see §7)
                 │
6. REFINE       conversational tweaks ("cheaper day 2", "add a beach day")
```

Steps 2–3 are the flagship agentic capability; step 5 is the differentiator
(actionable, not just advisory).

---

## 3. Core features (LOCKED) vs later

### Core (v1 — the system must do these)
| # | Feature | Primary AI pattern |
|---|---|---|
| C1 | **Requirement intake & clarification** — turn free-text + form into a strict `TripBrief` | Structured extraction |
| C2 | **Research & decision engine** — online search + historical data → ranked destinations/plans with rationale | **Agent** (tools + loop) |
| C3 | **Itinerary generation** — day-by-day plan grounded in real POIs | Structured output + tools |
| C4 | **Booking quick actions** — search + book flights & hotels in-app, human-confirmed | Tools + strict workflow (§7) |
| C5 | **Conversational refinement** — multi-turn edits to the plan | Chat memory + tools |

### Later (post-v1)
Packing lists, review summarization, real-time replanning on disruptions,
trip narrative/share, translation/phrasebook, group/collaborative planning.
(These reuse the same base; no new architecture.)

---

## 4. System base — architecture & structure (LOCKED)

Clean/hexagonal layering. **The domain layer has zero framework or vendor
dependencies.** AI, persistence, and external services are plug-in adapters.

### 4.0.0 Prerequisites & Docker (LOCKED — run before any development)

**No coding starts until prerequisites pass.** Humans and AI assistants run the check
script first; if a tool is missing, **install it** before proceeding to Phase 0.

#### Prerequisites check

Run from repo root:

```bash
# Unix / macOS / Git Bash
./scripts/check-prerequisites.sh

# Windows PowerShell
./scripts/check-prerequisites.ps1

# or via root package.json (wraps the above)
npm run prereq
```

The script **checks → reports → exits non-zero if anything missing**. Each failure
prints the tool name, required version, and **install hint** for the detected OS.

| Tool | Required version | Used for | If missing |
|---|---|---|---|
| **Node.js** | **22.x** | `apps/frontend`, root scripts | Install via [nvm](https://github.com/nvm-sh/nvm) / [fnm](https://github.com/Schniz/fnm) / official installer; `nvm install 22` |
| **npm** | 10+ (bundled with Node 22) | monorepo scripts, codegen | Comes with Node; update Node if npm too old |
| **Java JDK** | **21** | `apps/backend` compile & run | [Adoptium Temurin 21](https://adoptium.net/) or OS package manager |
| **Docker** | 24+ | container runtime | [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Win/Mac) or `docker.io` (Linux) |
| **Docker Compose** | v2+ | orchestrate stack | Included with Docker Desktop; `docker compose version` |
| **Git** | 2.x+ | version control | [git-scm.com](https://git-scm.com/) |
| **Gradle** | **8.x** (wrapper in repo) | `apps/backend` build | `./gradlew --version`; install from [gradle.org](https://gradle.org/install/) only if wrapper missing |

**Optional (recommended):**
| Tool | Purpose |
|---|---|
| `curl` / `Invoke-WebRequest` | health-check endpoints after `docker compose up` |
| `psql` client | manual DB inspection (not required — Flyway handles schema) |

**After install:** re-run `npm run prereq` until all checks pass.

**AI rule:** if prerequisite check fails, **stop and instruct the user to install** —
do not scaffold code on a machine missing Node 22, Java 21, or Docker.

#### Docker — full stack runtime (LOCKED)

The system **must run entirely via Docker Compose** for local and consistent environments.

```
travel-planner/
├── docker/
│   ├── backend/
│   │   └── Dockerfile              # multi-stage: build JAR → JRE 21 runtime
│   └── frontend/
│       └── Dockerfile              # multi-stage: next build → node 22 runner
├── docker-compose.yml              # full stack: postgres + backend + frontend
├── docker-compose.dev.yml          # optional: postgres only (apps run on host)
├── .env.example                    # committed template — copy to .env
└── scripts/
    ├── check-prerequisites.sh
    ├── check-prerequisites.ps1
    └── wait-for-services.sh        # block until postgres + backend healthy
```

##### Compose services

| Service | Image / build | Port (host) | Purpose |
|---|---|---|---|
| **postgres** | `pgvector/pgvector:pg16` | `5432` | PostgreSQL + pgvector |
| **backend** | `docker/backend/Dockerfile` | `8080` | Spring Boot API `/api/v1/` |
| **frontend** | `docker/frontend/Dockerfile` | `3000` | Next.js app |
| **redis** *(Phase 2+)* | `redis:7-alpine` | `6379` | Semantic cache (§5.3) — optional Compose profile `cache` |

**Health endpoints (Phase 0a):** backend exposes `GET /api/v1/health` (liveness) and
`GET /api/v1/ready` (readiness — DB reachable). `wait-for-services.sh` polls `/ready`.

##### Commands (root)

```bash
# Full stack (default)
docker compose up --build

# Detached
docker compose up -d --build

# DB only — frontend + backend on host (faster hot reload during dev)
docker compose -f docker-compose.dev.yml up -d

# Tear down
docker compose down

# Tear down + wipe volumes (fresh DB)
docker compose down -v

# Phase 2+ — include Redis for semantic cache
docker compose --profile cache up -d
```

##### Environment & secrets

- Copy `.env.example` → `.env` (gitignored) before first `docker compose up`.
- **Never commit** `.env` — API keys (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`) injected via env.
- Frontend in Docker: `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1`.
- Backend in Docker: `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/travel_planner`.

##### Docker rules

| Rule | Detail |
|---|---|
| **Dockerfile per app** | `docker/backend/`, `docker/frontend/` — not in app root |
| **Multi-stage builds** | Separate build and runtime stages — small final images |
| **Non-root user** | Runtime containers run as non-root where possible |
| **Health checks** | `HEALTHCHECK` in each Dockerfile; Compose `depends_on` with condition |
| **Flyway on startup** | Backend container runs migrations before accepting traffic |
| **Volume for Postgres** | Named volume `postgres_data` — persists between restarts |
| **CI parity** | CI builds same Dockerfiles — what runs locally matches pipeline |
| **No prod secrets in images** | Secrets via env at runtime only |

##### Development modes

| Mode | When | Command |
|---|---|---|
| **Full Docker** | Onboarding, QA, CI-like repro | `docker compose up --build` |
| **Hybrid** | Day-to-day dev (hot reload) | `docker compose -f docker-compose.dev.yml up -d` + run apps on host |
| **Host-only DB** | Backend/frontend debugging | Postgres in Docker; Node + Java on host |

Both modes require **`npm run prereq`** to pass first.

### 4.0 Monorepo layout & runtime requirements (LOCKED)

This project lives in **one monorepo** that owns both the frontend and backend.
No split repos, no duplicated contracts, no drift between apps.

```
travel-planner/                    # monorepo root
├── apps/
│   ├── frontend/                  # Next.js + TypeScript  →  Node.js 22
│   └── backend/                   # Spring Boot 3.x        →  Java 21
├── docker/                        # Dockerfiles (§4.0.0)
├── scripts/                       # check-prerequisites, wait-for-services
├── docs/adr/                      # architecture decision records
├── packages/                      # shared libs — only when criteria in §4.0 met
├── plans/
│   ├── superpower/PLAN.md         # architecture & rules (this file)
│   └── BACKLOG.md                 # sprint-ready epics & stories
├── .nvmrc                         # 22
├── .env.example                   # env template for docker compose
├── package.json                   # root scripts: prereq, dev, codegen, docker:*
├── docker-compose.yml             # full stack (§4.0.0)
└── docker-compose.dev.yml         # postgres only
```

| App | Runtime | Enforcement |
|---|---|---|
| `apps/frontend` | **Node.js 22** | `.nvmrc`, `package.json` `"engines": { "node": ">=22" }`, CI node-version check |
| `apps/backend` | **Java 21** | Gradle toolchain (`build.gradle.kts`), `JAVA_HOME`, CI java-version check |

**`packages/` extraction criteria (do not add prematurely):**
- Same code imported by **2+ apps**, AND
- OpenAPI codegen **cannot** cover it (e.g. shared ESLint config, not API types).
- Never duplicate DTO shapes — regenerate from OpenAPI instead.

**Monorepo rules:**
- Run **`npm run prereq`** before first dev session (§4.0.0).
- Full stack via **`docker compose up`** (§4.0.0).
- OpenAPI spec lives in `apps/backend` and is the **single contract source** for both apps.
- Codegen runs from the repo root (`openapi → apps/frontend/src/generated/...`).
- Shared scripts (dev, test, migrate, codegen) are invoked from root `package.json` or a `Makefile`.
- Do **not** copy DTO shapes by hand into the frontend — always regenerate from OpenAPI.

### 4.0.1 Backend layer discipline — Controller vs Service (LOCKED)

Spring `@RestController` classes are **thin routing adapters only**. All business
logic, orchestration, validation of domain rules, and transaction boundaries live in
the **Service layer** (`application/` use-cases).

| Layer | Package | Allowed | Forbidden |
|---|---|---|---|
| **Controller** | `api/controller/` | HTTP mapping, auth/permission checks, request DTO binding, delegate to service, map service result → response DTO, HTTP status codes | Business rules, DB access, LLM calls, supplier API calls, branching logic beyond input validation |
| **Service** | `application/` | Use-case orchestration, domain invariants, calling ports (LLM, search, booking), transaction boundaries, composing domain objects | HTTP concerns (`ResponseEntity`, status codes), framework web types |
| **Domain** | `domain/` | Pure model + port interfaces | Any framework or vendor import |
| **Infrastructure** | `infrastructure/`, `ai/` | Port implementations, persistence, external APIs | Business rules that belong in domain/application |

```java
// ✅ Controller — routing only; kebab-case URL; ≤3 service args via DTOs
@PostMapping("/api/v1/trips/{tripId}/ranked-recommendations")
public ResearchResponse research(@PathVariable UUID tripId,
        @Valid @RequestBody ResearchRequest req,
        @AuthenticationPrincipal UserContext user) {
    return researchMapper.toResponse(
        researchService.runResearch(req.toQuery(tripId), user));
}

// ❌ Controller — business logic belongs in ResearchService, not here
@PostMapping("/api/v1/trips/{id}/research")
public ResearchResponse research(@PathVariable UUID id, @RequestBody ResearchRequest req) {
    var trip = tripRepo.findById(id).orElseThrow();
    if (trip.getBudget().isLessThan(req.getMaxSpend())) { ... }  // NO
    return llmClient.complete(...);                                // NO
}
```

If a Controller method grows beyond ~10 lines or contains an `if` that encodes a
business rule, **move it to a Service**.

**Controller param count:** `@PathVariable` + `@RequestBody` + `@AuthenticationPrincipal`
counts as **3** — the maximum for controller methods. Path id is not a separate business
argument; bind into `*Query` inside the mapper when possible.

```java
// ✅ 3 params max at controller boundary
@PostMapping("/api/v1/trips/{tripId}/ranked-recommendations")
public ResearchResponse research(@PathVariable UUID tripId,
        @Valid @RequestBody ResearchRequest req,
        @AuthenticationPrincipal UserContext user) {
    return researchMapper.toResponse(
        researchService.runResearch(req.toQuery(tripId), user));
}
```

### 4.0.2 Backend coding rules (LOCKED)

All backend code — human-written or AI-generated — MUST follow these rules.

##### A. Java & typing

| Rule | Detail |
|---|---|
| **Java 21** | Records, sealed types, pattern matching — compile target enforced in CI |
| **No raw primitives for domain concepts** | Use value objects (`Money`, `DateRange`) — never `double` for money |
| **Immutability preferred** | Records for DTOs/commands; domain objects enforce invariants in constructors |
| **Nullable discipline** | Use `Optional` at boundaries; domain core avoids null by design |
| **No `var` abuse** | Use when type is obvious from RHS; explicit types for public APIs |

##### B. Naming & files

| Kind | Convention | Example |
|---|---|---|
| **Functions / methods** | **camelCase** | `runResearch()`, `fetchResearch()` |
| **REST URL paths** | **kebab-case** | `/trips/{id}/ranked-recommendations` |
| **Java class file** | PascalCase (language requirement) | `ResearchService.java` |
| Controller | `*Controller.java` | `ResearchController.java` |
| Service / use-case | `*Service.java` | `ResearchService.java` |
| Query / Command DTO | `*Query` / `*Command` records | `ResearchQuery`, `SaveTripBriefCommand` |
| Context DTO | `*Context` record | `UserContext`, `RequestContext` |
| Domain model | PascalCase noun | `Trip`, `TripBrief` |
| Port | `*Port.java` interface | `LlmPort`, `SearchPort` |
| DB columns / tables | **snake_case** | `trip_brief`, `created_at` |

One public class per file. Package by layer, then feature (`application/research/`).

##### B2. Function parameters (≤3 — LOCKED)

Keep every function/method signature **as short as possible**. **Maximum 3 parameters.**
When more data is needed, **bundle into a typed DTO** (NestJS-style) — never add a 4th
param; extend the DTO instead.

| Param slot | Typical DTO | Purpose |
|---|---|---|
| 1 | `*Query` / `*Command` | Input data for the operation |
| 2 | `UserContext` / `ActorContext` | Who is calling (auth, tenant, locale) |
| 3 | `RequestContext` (optional) | Trace id, idempotency key, request metadata — only when needed |

```java
// ✅ NestJS-style — 2 params, clear intent
public RankedRecommendations runResearch(ResearchQuery query, UserContext user) { ... }

// ✅ 1 param — command object carries everything
public TripBrief saveBrief(SaveTripBriefCommand command) { ... }

// ❌ too many params — bundle into ResearchQuery
public RankedRecommendations runResearch(UUID tripId, UUID userId, Money budget,
        DateRange dates, List<String> interests, int maxResults) { ... }
```

**Use interfaces + records/DTOs to control fields** — callers cannot pass unbounded
primitives; fields are declared, validated (`@Valid`, `@NotNull`), and documented in one place.

```java
// api/dto/query/ResearchQuery.java
public record ResearchQuery(
    @NotNull UUID tripId,
    Money maxSpend,
    @Size(max = 10) List<String> interests
) {}

// domain/port/ResearchPort.java — interface controls contract
public interface ResearchPort {
    RankedRecommendations research(ResearchQuery query);
}
```

##### B3. Human-readable code

| Rule | Detail |
|---|---|
| **Name by intent** | `runResearch` not `doStuff`; `ResearchQuery` not `Data` |
| **One level of abstraction per method** | Extract when a method mixes validation + IO + mapping |
| **Early returns** | Guard clauses for errors — avoid deep nesting |
| **No magic numbers/strings** | Named constants or enum |
| **Javadoc on public API** | Service public methods + domain ports — one-line *what*, not *how* |
| **Max method length ~40 lines** | Split into private helpers or domain algorithm class |

##### C. Layer boundaries (summary)

| Layer | Package | Does | Does NOT |
|---|---|---|---|
| Controller | `api/controller/` | Route, bind DTO, delegate, map response | Business logic, DB, LLM, supplier calls |
| Service | `application/` | Orchestrate, enforce rules, transactions, call ports | HTTP types, JPA entities, LangChain4j |
| Domain | `domain/` | Model, invariants, port interfaces | Any framework/vendor import |
| AI | `ai/` | LLM agents, prompts, LangChain4j adapters | Business rules, HTTP, persistence |
| Infrastructure | `infrastructure/` | JPA, external APIs, port implementations | Domain business rules |
| API DTO | `api/dto/` | Wire format | Domain logic |

Dependencies point **inward only**: `api → application → domain`.

##### D. Controllers

- **≤10 lines per endpoint** — validate → delegate → map.
- Inject **services only** — never repositories, `LlmClient`, or supplier clients.
- Use `@Valid` on request DTOs; no manual field-null checks that encode business rules.
- Return response DTOs — never domain entities or JPA entities.

##### E. Services

- All business logic and orchestration lives here.
- Inject **port interfaces** (`LlmPort`, `TripRepository` as port) — not concrete adapters.
- **Constructor injection only** — `@RequiredArgsConstructor` + `final` fields; no field `@Autowired`.
- `@Transactional` on write use-cases; `@Transactional(readOnly = true)` on queries.
- **Return domain objects** — controller maps domain → response DTO via MapStruct (locked pattern).
- Unit-test with mocked ports.

##### E2. Annotations & validation (backend)

Spring/Jakarta **annotations and decorators are encouraged** where they reduce boilerplate:

| Use | Annotation |
|---|---|
| REST routing | `@RestController`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` |
| Request validation | `@Valid`, `@NotNull`, `@Size`, `@Min` on DTO records |
| Auth user | `@AuthenticationPrincipal UserContext user` |
| Transactions | `@Transactional`, `@Transactional(readOnly = true)` |
| Exception mapping | `@ControllerAdvice`, `@ExceptionHandler` |
| DI | `@Service`, `@Component`, `@RequiredArgsConstructor` |

Validation at **DTO boundary** via annotations; **business invariants** in domain constructors
— never duplicate the same rule in both places.

##### F. Domain

- Zero imports from Spring, JPA, LangChain4j, HTTP clients.
- Value objects validate in constructor (`Money` cannot be negative, etc.).
- Port interfaces define what the domain needs from the outside world.
- Aggregates enforce invariants — no setters that bypass rules.

##### G. AI / LLM

- LangChain4j imports **only** in `ai/langchain4j/`.
- Services call `LlmPort` or feature agents (`TravelResearchAgent`) — never LangChain4j APIs directly.
- LLM output is **structured + validated** (`Guardrails`) before persist or side effects.
- Agent proposes; **never auto-books** (see §7).

##### H. Persistence

- JPA entities live **only** in `infrastructure/persistence/`.
- Spring Data repositories in `infrastructure/persistence/` only — never in `application/`.
- Map JPA entity ↔ domain model in adapter/mapper — entities never leak to service or API.
- Flyway for all schema changes — no `ddl-auto=update` in production.
- Migration files: `V{version}__{snake_case_description}.sql` (e.g. `V1__create_trip_table.sql`).
- Money stored as `numeric` + currency column — never float.
- **Audit columns** on all mutable tables: `created_at`, `updated_at` (`timestamptz`, UTC).
- **Primary keys:** UUID v4 — column `id UUID PRIMARY KEY`.
- **Timestamps:** always `timestamptz`, stored UTC; display in user's locale on frontend.

##### I. DTOs & mapping

- **DTO ≠ domain model ≠ JPA entity** — three types, explicit mappers (MapStruct).
- OpenAPI spec in `api/openapi/` is source of truth for frontend codegen.
- Do not expose internal domain fields (e.g. embedding vectors) via API DTOs.

##### J. Error handling

- Typed domain exceptions extending `DomainException` with **snake_case `error_code`**.
- Mapped to standard envelope via `@ControllerAdvice` (see §6.1) — never raw stack traces.
- Booking failures return typed reason codes (see §7).
- Error codes registered in OpenAPI + mapped to i18n keys on frontend.

##### J2. Logging & tracing

- **Structured JSON logs** (Spring Boot default) — no PII (email, name) in logs.
- **`X-Request-Id`** header: frontend generates UUID → passes on every API call → backend
  includes in logs and MDC.
- Log levels: `ERROR` failures, `WARN` retries/fallbacks, `INFO` state transitions (booking),
  `DEBUG` dev only.
- LLM prompts/responses: log token counts + latency to `ai_call_log` — not full prompt text in prod.

##### K. Testing

| Target | Rule |
|---|---|
| Service | Unit test with mocked ports — cover business rules |
| Controller | Slice test or MockMvc — verify routing only, mock service |
| Domain | Pure unit tests — no Spring context |
| LLM output | Golden-file + schema contract tests |
| Mapper | MapStruct compile-time + spot integration tests |
| Integration | `src/test/integration/` with Testcontainers Postgres |

Test files: `{class-under-test}Test.java` (e.g. `ResearchServiceTest.java`).

##### L. Security & config

- Secrets via env / secret manager — never in source.
- Rate-limit AI and booking endpoints.
- Audit all money-touching state transitions.
- **CORS:** explicit allowed origins per env — no wildcard in production.
- **User scope:** all queries filter by `user_id` from `UserContext` — no cross-user data access.

##### M. AI-generated backend code — extra rules

1. **Never put logic in controllers** — if you wrote an `if` with a business rule, move to service.
2. **Never import LangChain4j outside `ai/langchain4j/`**.
3. **Never return JPA entities from controllers**.
4. **Follow package structure** — new feature → `application/<feature>/` + `api/controller/` + ports in `domain/port/`.
5. **Self-check against §4.0.1 layer table** before finishing.

### 4.0.3 Data structures & algorithms (when needed)

Use **data structures and algorithms (DSA)** when they materially improve **performance**,
**correctness**, or **determinism** — not for cleverness or premature optimization.

LLMs handle fuzzy reasoning; DSA handles **precise, repeatable computation** the product
depends on (ranking, scheduling, overlap checks, aggregation). When both apply, combine them:
LLM proposes → DSA validates/ranks/optimizes → typed result to UI.

#### When to introduce DSA

| Trigger | Example in Travel Planner |
|---|---|
| **Correctness** | Date-range overlap detection; budget constraint filtering; booking state validity |
| **Performance** | Ranking 500+ destination candidates; aggregating price history over 12 months |
| **Optimization goal** | Day-by-day POI ordering (min travel time); multi-city route sequencing |
| **Determinism** | Same inputs → same ranked order (tie-break rules); reproducible eval harness scores |
| **Scale** | Large result sets from supplier APIs needing top-K without full sort |

#### When NOT to use DSA

- Subjective travel advice ("romantic", "hidden gem") — LLM + human review.
- One-off list operations on < ~50 items — plain loops are fine.
- Before measuring — **no optimization without a proven bottleneck** (log, profiler, or complexity argument).
- Replacing LLM structured extraction — use JSON schema + Guardrails instead.

#### Where code lives

| Location | Purpose |
|---|---|
| **`domain/algorithm/`** | Pure DSA — no Spring, no I/O. Input domain types → output domain types. |
| **`domain/valueobject/`** | Small invariant helpers (e.g. `DateRange.overlaps()`) — not full algorithms. |
| **`application/`** | Orchestrates: load data via ports → call algorithm → persist/act on result. |
| **`infrastructure/`** | DB-level structures (pgvector index, SQL window functions) when push-down is faster. |
| **`apps/frontend/src/lib/algorithms/`** | UI-only: client sort/filter, memoized derivations, virtual list helpers — no business rules. |

```
domain/
├── algorithm/
│   ├── ranking/          # DestinationScorer, WeightedScoreCalculator, TopKSelector
│   ├── scheduling/       # ItineraryDayPlanner, TimeSlotAllocator (greedy / constraint sat)
│   ├── graph/            # PoiRouteOptimizer, CityGraph (shortest path variants)
│   ├── interval/         # DateRangeIndex, overlap queries
│   └── aggregation/      # PriceTrendAggregator, SeasonalityIndex
```

#### Planned use-cases by feature

| Feature | Algorithm / structure | Goal |
|---|---|---|
| **C2 Research** | Weighted scoring + **priority queue / partial sort** for top-K | Rank destinations by budget fit, season, interests |
| **C2 Historical** | **Sliding window** / time-series aggregation on `price_history` | Trend direction, best booking window |
| **C3 Itinerary** | **Greedy + constraints** or TSP heuristic on POI graph | Minimize transit; respect opening hours & pace |
| **C3 Itinerary** | **Interval tree** or sorted intervals on `DateRange` | Detect day overlap, travel-day conflicts |
| **C4 Booking** | Sort + **hash map** lookup by quote id; dedup via idempotency set | Compare quotes; prevent double-submit |
| **RAG / search** | pgvector **ANN index** (HNSW/IVFFlat) — DB structure, not app heap | Fast similarity search at scale |
| **C5 Chat refine** | Diff/patch on itinerary tree — structured edit, not free-text replace | Apply "move POI to day 2" deterministically |
| **Frontend lists** | **Virtual scroll** + memoized sort (React) | Render 100+ recommendations/itinerary items |

#### Coding rules for DSA

| Rule | Detail |
|---|---|
| **Pure functions** | Algorithm classes are stateless; all inputs passed explicitly |
| **Typed I/O** | Accept/return domain types — no `Map<String, Object>` |
| **Unit tested** | Table-driven tests with edge cases (empty input, ties, single item, max bounds) |
| **Document complexity** | Javadoc `@implNote O(n log n)` when non-obvious or performance-critical |
| **Name by intent** | `DestinationRanker`, not `SortUtil` — reflects business goal |
| **No micro-DSA in controllers/services** | Extract to `domain/algorithm/` when logic exceeds ~15 lines or is reused |
| **Benchmark gate** | For hot paths, add a micro-benchmark or log timing before/after; keep in `src/test` |
| **Prefer DB when appropriate** | Large aggregations → SQL window functions / indexes; don't load millions of rows into heap |
| **LLM boundary** | DSA runs **after** LLM output is parsed to typed objects — validates and refines, doesn't parse text |

```java
// domain/algorithm/ranking/DestinationRanker.java — pure, testable
public final class DestinationRanker {
    public List<RankedDestination> topK(List<DestinationCandidate> candidates,
                                        ScoringWeights weights, int k) {
        // weighted score → priority queue → top K
    }
}

// application/research/ResearchService.java — orchestrates
var ranked = destinationRanker.topK(candidates, weights, TOP_K);
```

#### Frontend DSA (lightweight)

Only for **UI performance and presentation** — business ranking/scoring stays on backend.

| Use | Where |
|---|---|
| Memoized sorted/filtered lists | `features/*/hooks/useSortedRecommendations.ts` |
| Debounce / throttle on search | `lib/utils/` or hook |
| Virtual scrolling (100+ rows) | `components/ui/virtual-list.tsx` |
| Client-side grouping by day | `features/itinerary/hooks/` — display only; server is source of truth |

```
com.travelplanner
├── domain/                 # PURE: no Spring, no LangChain, no JPA annotations leaking rules
│   ├── model/              #   entities + aggregates (Trip, Itinerary, Booking)
│   ├── valueobject/        #   Money, DateRange, GeoLocation, PartySize, TripBrief
│   ├── enums/              #   CabinClass, TripStyle, BookingStatus, Provider
│   ├── algorithm/          #   pure DSA: ranking, scheduling, graph, interval, aggregation (§4.0.3)
│   └── port/               #   interfaces the domain needs (SearchPort, BookingPort, LlmPort...)
│
├── application/            # use-cases / orchestration (services); depends only on domain ports
│   ├── intake/  research/  itinerary/  booking/  chat/
│
├── ai/                     # LLM concerns; implements domain LlmPort via LangChain4j
│   ├── client/             #   LlmClient/EmbeddingClient interfaces + LlmClientRouter (§5.4)
│   ├── langchain4j/        #   the ONLY package that imports LangChain4j
│   ├── agent/              #   TravelResearchAgent, agent loop control, step budgets
│   ├── tool/               #   tool definitions (web search, flights, hotels, history...)
│   ├── prompt/             #   versioned prompt templates (+ per-provider variants)
│   └── structured/         #   JSON-schema binding + validation of model output
│
├── infrastructure/         # adapters to the outside world (implement domain ports)
│   ├── persistence/        #   JPA entities + repositories + Flyway migrations
│   ├── search/             #   web search API client
│   ├── flights/  hotels/   #   supplier API clients
│   ├── payment/            #   payment provider client (tokenized; §7)
│   └── history/            #   historical price/weather/seasonality data access
│
├── api/                    # REST layer
│   ├── controller/  dto/  mapper/   # DTOs are separate from domain; MapStruct maps between
│   └── openapi/            #   spec = source of truth for the TS client
│
└── config/                 # Spring config, provider routing, secrets wiring
```

**Rules that keep it "strict structure":**
- Dependencies point **inward** only: `api → application → domain`; `ai`/`infrastructure`
  implement `domain.port` interfaces. Domain never imports outward.
- **Controllers delegate; Services decide.** See §4.0.1 — no exceptions.
- **No vendor type crosses a layer boundary.** LangChain4j types live only in
  `ai/langchain4j`; JPA entities never leave `infrastructure/persistence`
  (map to domain models).
- **DTO ≠ domain model ≠ JPA entity** — three separate types, explicit mappers.
  Prevents accidental over-exposure and keeps each strict.
- Every aggregate has an invariant-enforcing constructor; no half-built objects.

### 4.0.4 Shared code conventions — frontend & backend (LOCKED)

These rules apply to **both** apps unless a language/framework exception is noted.
See also backend §4.0.2-B2/B3 and frontend §4.2.6.

#### Function signatures — max 3 parameters

Keep every function/method **as short as possible**. **Maximum 3 parameters.**
When more data is needed, **bundle into a typed DTO** (NestJS-style) — extend the DTO,
never add a 4th parameter.

| Param slot | Backend | Frontend |
|---|---|---|
| 1 | `*Query` / `*Command` record | `*Request` / `*Params` interface |
| 2 | `UserContext` / `ActorContext` | `UserContext` from session hook |
| 3 | `RequestContext` (optional) | Idempotency / trace metadata — rare |

```java
// ✅ Backend — 2 params (NestJS-style)
public RankedRecommendations runResearch(ResearchQuery query, UserContext user) { ... }
```

```typescript
// ✅ Frontend — 1 param (interface)
export async function fetchResearch(request: FetchResearchRequest): Promise<RankedRecommendations> { ... }

// ✅ Component — 1 props interface counts as 1 param
interface ResearchPanelProps { tripId: string; }
export function ResearchPanel({ tripId }: ResearchPanelProps) { ... }

// ❌ — bundle into FetchResearchRequest
function fetchResearch(tripId: string, userId: string, budget: number, locale: string) { ... }
```

#### Interfaces control fields

| Layer | Mechanism |
|---|---|
| Backend API | Java `record` + Jakarta `@Valid` / `@NotNull` |
| Backend domain | Port `interface` + value object invariants |
| Backend service | `*Query`, `*Command`, `*Context` records |
| Frontend props | `interface XxxProps` on every component |
| Frontend forms | `interface XxxFormValues` — mapped to API in hook |
| Frontend API | Wrap generated types; no `Record<string, unknown>` |

#### Naming (LOCKED)

| Target | Convention | Example |
|---|---|---|
| Functions / methods | **camelCase** | `runResearch`, `useResearchQuery` |
| React components | PascalCase (JSX) | `ResearchPanel` |
| Java classes / records | PascalCase | `ResearchQuery` |
| Frontend files | **kebab-case** | `research-panel.tsx`, `research-api.ts` |
| CSS modules | kebab-case | `research-panel.module.css` — **deprecated; use Tailwind** |
| URLs / API paths | **kebab-case** | `/trips/{id}/ranked-recommendations` |
| Translation files | **snake_case** | `trip_brief.json`, `booking_flow.json` |
| Translation keys | **snake_case** | `t('trip_brief.destinations_label')` |
| DB tables / columns | snake_case | `trip_brief`, `created_at` |
| Java source files | PascalCase | Language requirement — `ResearchService.java` |
| **Config exceptions** | lowercase single word OK | `client.ts`, `index.ts`, `tokens.ts`, `ant-theme.ts` (not kebab-case) |

#### Human-maintainable code

- **Read like prose** — understandable without jumping across many files.
- **Explicit over clever** — DTO + 2 params beats varargs or generic maps.
- **Small units** — one file, one primary export, one reason to change.
- **Comments explain why**, not what.
- **Field parity** — OpenAPI schema ↔ backend DTO ↔ frontend request interface (codegen where possible).
- **Max file length ~300 lines** — split when exceeded.
- **No commented-out code** in PRs; `TODO(username): description` only with ticket reference.

#### ≤3 params — exceptions (LOCKED)

| Context | Rule |
|---|---|
| **Service / domain / algorithm methods** | Max 3 — bundle into `*Query`, `*Command`, `*Context` |
| **Controller methods** | Max 3: `@PathVariable` + body DTO + `@AuthenticationPrincipal UserContext` |
| **Constructors (Spring DI)** | **Exempt** — inject as many dependencies as needed via constructor |
| **Private helpers** | Same ≤3 rule |
| **Generated / framework code** | Exempt |

### 4.0.5 Auth — user-based, no tenant (LOCKED)

This is a **single-user-account** system — not multi-tenant SaaS.

| Decision | Detail |
|---|---|
| **Account model** | One **User** owns their trips, briefs, bookings, conversations |
| **No tenant** | No `tenant_id`, no org/workspace hierarchy, no tenant admin |
| **UserContext** | `{ userId, email, roles[] }` — passed as param 2 or `@AuthenticationPrincipal` |
| **Data isolation** | Every query scoped by `user_id`; services reject cross-user access — **except `ROLE_ADMIN`** (§4.0.6) |
| **Roles** | `ROLE_USER` (default) · `ROLE_ADMIN` (user management) |
| **Auth from v1** | Registered user accounts (not guest-only) |
| **Token transport** | Self-issued **JWT** returned on login; stored in **httpOnly, Secure, SameSite** cookie |
| **Login API** | `POST /api/v1/auth/login` → sets cookie; `POST /api/v1/auth/logout` clears it |
| **Session check** | `GET /api/v1/auth/me` → current user + roles for `useUserContext()` |
| **OAuth** | Google/GitHub — **post-v1** add-on; router stays open, not in Phase 0 |
| **Frontend** | Credentials via cookie (`credentials: 'include'` in `lib/api/client.ts`); no tenant selector |

```java
// UserContext — record, not a loose map
public record UserContext(@NotNull UUID userId, String email, List<String> roles) {}

// ✅ Service always scopes to user
public Trip getTrip(GetTripQuery query, UserContext user) {
    return tripPort.findByIdAndUserId(query.tripId(), user.userId())
        .orElseThrow(() -> new TripNotFoundException(query.tripId()));
}
```

**API calls:** frontend calls Spring Boot **directly** (no BFF) with auth header/cookie.
Next.js Route Handlers only for health/static — not for proxying business APIs.

### 4.0.6 Admin role & database seeder (LOCKED)

A built-in **admin account** supports viewing and assisting other users (e.g. password
reset). Regular users still own their own trips; admins operate on **accounts**, not
tenant/org hierarchies.

#### Seeded admin account (dev / Docker / local)

| Field | Value | Notes |
|---|---|---|
| **Username** | `ADMIN` | Unique; login is case-insensitive |
| **Password** | `123456` | **Dev/docker seed only** — BCrypt-hashed in DB, never stored plain |
| **Role** | `ROLE_ADMIN` | Spring Security authority |

> **Security:** `ADMIN` / `123456` is intentionally weak for **local development and
> Docker only**. Production deployments **must** disable this seed (see below) or force
> password change on first login. Never use these credentials in production.

#### Where the seeder lives

```
apps/backend/src/main/resources/db/
├── migration/
│   ├── V1__create_user_table.sql
│   └── V2__seed_dev_admin_user.sql      # idempotent INSERT — dev/docker profile
└── seed/
    └── admin-user.sql                   # reference copy of seed SQL
```

**Flyway seed migration** `V2__seed_dev_admin_user.sql`:
- Runs with Flyway on backend startup (Docker + local dev).
- **Idempotent** — `INSERT ... ON CONFLICT (username) DO NOTHING` (or equivalent).
- Stores **BCrypt hash** of `123456` — generate hash at build time or use a known test hash.
- Sets `role = 'ADMIN'`, `enabled = true`.

**Profile gating:**

| Profile | Seed runs? |
|---|---|
| `dev`, `docker`, `local` | **Yes** — admin available after `docker compose up` |
| `prod` | **No** — skip `V2__seed_dev_admin_user` via Flyway placeholder or separate prod migration set |

Alternatively: seed via `AdminUserSeeder` (`@Profile("dev | docker | local")`) if SQL
seed is awkward — same credentials, same idempotent upsert.

**Docker:** admin ready after first `docker compose up --build` + Flyway migrate.

#### Admin capabilities (v1)

| Action | API | Who |
|---|---|---|
| List all users | `GET /api/v1/admin/users` | `ROLE_ADMIN` |
| View user detail | `GET /api/v1/admin/users/{userId}` | `ROLE_ADMIN` |
| Reset user password | `PUT /api/v1/admin/users/{userId}/reset-password` | `ROLE_ADMIN` |
| Update user (enable/disable) | `PUT /api/v1/admin/users/{userId}` | `ROLE_ADMIN` |

- Admin endpoints under **`api/admin/`** package — separate from user trip APIs.
- **`@PreAuthorize("hasRole('ADMIN')")`** on admin controller methods.
- Password reset: admin sets a **temporary password**; user forced to change on next login (later) or admin communicates temp password out-of-band.
- Every admin action writes to **`audit_event`** (who, what, target user, when).

```java
// application/admin/AdminUserService.java
@PreAuthorize("hasRole('ADMIN')")
public void resetPassword(ResetPasswordCommand command, UserContext admin) {
    auditPort.logAdminAction(admin.userId(), "reset_password", command.userId());
    userPort.resetPassword(command.userId(), passwordEncoder.encode(command.newPassword()));
}
```

#### Backend rules for admin

| Rule | Detail |
|---|---|
| **Separate service** | `application/admin/AdminUserService` — not mixed into trip services |
| **No trip access by default** | Admin manages **accounts**; viewing another user's trips is a separate explicit action (post-v1 unless needed) |
| **Audit everything** | All admin mutations → `audit_event` |
| **≤3 params** | e.g. `resetPassword(ResetPasswordCommand cmd, UserContext admin)` |
| **Standard errors** | `forbidden`, `user_not_found` via §6.1 envelope |

#### Frontend (admin UI)

```
app/(admin)/
├── layout.tsx              # admin shell — guard ROLE_ADMIN
└── users/
    ├── page.tsx            # list users
    └── [userId]/page.tsx   # view user + reset password form

features/admin/
├── components/
│   ├── user-list-panel.tsx
│   └── reset-password-form.tsx
├── hooks/
│   └── use-admin-users.ts
└── index.ts
```

- Route guard: redirect non-admin to planner home.
- Reset password form: admin enters new password → `PUT .../reset-password`.
- i18n namespace: `admin.json` (snake_case keys).

#### Login

- Same login endpoint as regular users — username `ADMIN`, password `123456`.
- JWT includes `roles: ["ADMIN"]` → unlocks `(admin)` routes.

### 4.0.7 External adapters — stub-first policy (LOCKED)

Vendor APIs (web search, flights, hotels, payments) are **not** blockers for feature
delivery. Every domain port gets a **stub adapter** before a real one.

| Rule | Detail |
|---|---|
| **Port first** | Define interface in `domain/port/` before any HTTP client |
| **Stub ships with feature** | `infrastructure/<vendor>/Stub*Adapter` returns realistic fixture data |
| **Profile switch** | `application.yml`: `adapters.search=stub` \| `live` — default `stub` in dev |
| **Contract tests** | Stub and live adapters share the same port contract tests |
| **No controller stubs** | Stubs live in `infrastructure/` only — never fake logic in controllers |
| **Phase 1 default** | C2/C3 use stubs for search + suppliers; real providers wired in Phase 2 |

```
infrastructure/
├── search/
│   ├── StubSearchAdapter.java      # fixture JSON — ships in Sprint 4 (C2)
│   └── LiveSearchAdapter.java      # real API — when vendor chosen (§11)
├── flights/
│   ├── StubFlightSearchAdapter.java
│   └── LiveFlightSearchAdapter.java
└── hotels/
    ├── StubHotelSearchAdapter.java
    └── LiveHotelSearchAdapter.java
```

**AI rule:** if a vendor is undecided, implement the port + stub and continue — do not
wait for procurement.

### 4.1 The research & decision agent (C2) — how it works

A **bounded** LangChain4j agent (max N tool calls, hard token/step budget):

```
TripBrief ──► TravelResearchAgent
                 loops with tools:
                   • WebSearchTool          (live info: events, advisories, deals)
                   • HistoricalPriceTool     (our DB: price trend & best-time-to-buy)
                   • SeasonalityWeatherTool  (climate, crowd levels by month)
                   • FlightSearchTool        (indicative fares)
                   • HotelSearchTool         (indicative rates)
                   • CurrencyTool            (normalize to user's currency)
                 ──► emits RankedRecommendations (STRUCTURED, validated):
                     [{ destination, estCost(Money), fitScore, rationale,
                        bestWindow, risks }]
```

- **Live vs historical are distinct data classes.** "Online search" = fresh facts;
  "historical data" = stored trends we own (price index, seasonality). The agent
  combines both; each recommendation cites which sources it used.
- Output is a typed `RankedRecommendations` object — **never** free text — so the UI
  and downstream itinerary step consume it safely.
- The agent **proposes**; it never books. Booking is a separate, human-gated action (§7).
- Deterministic guardrails: budget filter, date/plausibility checks, and a fallback
  ("no confident recommendation" is a valid, typed result, not a hallucinated one).

### 4.2 Frontend architecture — Option A feature-based (LOCKED)

The frontend mirrors the backend's separation of concerns: **pages route, features
own UI logic, API layer talks to the backend, generated types are the contract.**

**Structure choice: Option A (feature-based) is locked.** Options B (route-colocated)
and C (layered services) were considered and rejected — they split feature logic across
folders and do not map cleanly to C1–C5.

#### 4.2.1 Locked frontend decisions

| Area | Decision |
|---|---|
| Framework | **Next.js 15+ App Router** (`app/`) — no Pages Router |
| UI library | **Ant Design** — forms, tables, modals, layout |
| Forms | Ant Design `Form` with **`onValuesChange`** for controlled, incremental updates |
| Data fetching | **TanStack Query (React Query)** — cache, loading/error states, mutations |
| **i18n** | **next-intl** — all user-facing strings via `t()`; no hardcoded copy (§4.2.10) |
| API types | **Generated only** from OpenAPI (`src/generated/api/`) — never hand-written DTOs |
| Runtime validation | **Zod** at the API boundary (validate responses before UI consumes) |
| Styling | **Tailwind CSS** (layout + Ant overrides) + **Ant Design** (components) — unified tokens (§4.2.9) |
| State | Server state → React Query; UI/ephemeral state → React `useState`/`useReducer`; no Redux unless a later need is proven |

#### 4.2.2 Frontend layer discipline

Mirrors backend §4.0.1 — keep each layer thin and single-purpose.

| Layer | Location | Allowed | Forbidden |
|---|---|---|---|
| **Page / Route** | `app/**/page.tsx` | Compose feature components, load params, set metadata | Direct `fetch`, business rules, form field logic, Ant Design form state |
| **Feature module** | `features/<name>/` | Screen composition, hooks, form schemas, feature-specific components | Cross-feature imports (use shared `components/` instead) |
| **API client** | `lib/api/` | Thin wrappers over generated client, auth headers, base URL, error mapping | UI rendering, React hooks |
| **Hooks** | `features/*/hooks/` or `hooks/` | React Query queries/mutations, derived UI state | Raw fetch bypassing generated types |
| **Components (shared)** | `components/ui/`, `components/layout/` | Reusable presentational pieces | Feature-specific business logic |
| **Generated** | `generated/api/` | Auto-generated — **do not edit** | Any manual changes |

```tsx
// ✅ Page — routing & composition only
export default function TripResearchPage({ params }: { params: { tripId: string } }) {
  return <ResearchPanel tripId={params.tripId} />;
}

// ❌ Page — logic belongs in features/research/
export default function TripResearchPage({ params }) {
  const [data, setData] = useState(null);
  useEffect(() => { fetch(`/api/trips/${params.tripId}/research`)... }, []);  // NO
  return <Form onFinish={async (v) => { /* 50 lines of logic */ }} />;         // NO
}
```

#### 4.2.3 Route map (aligned to user flow §2 & features C1–C5)

```
app/
├── (marketing)/              # optional landing — no auth required
│   └── page.tsx
├── (planner)/                # authenticated trip planning shell (shared layout)
│   ├── layout.tsx            #   Ant Design Layout: sidebar, header, trip context
│   ├── trips/
│   │   ├── page.tsx          #   trip list
│   │   ├── new/page.tsx      #   C1 intake — create TripBrief
│   │   └── [tripId]/
│   │       ├── page.tsx        #   trip overview / stepper
│   │       ├── brief/page.tsx          #   C1 — edit TripBrief
│   │       ├── research/page.tsx       #   C2 — ranked recommendations
│   │       ├── itinerary/page.tsx      #   C3 — day-by-day plan
│   │       ├── booking/page.tsx        #   C4 — flights/hotels + confirm
│   │       └── chat/page.tsx           #   C5 — conversational refinement
│   └── settings/page.tsx
└── api/                      # optional Next.js route handlers (BFF) — use sparingly
    └── health/route.ts       #   prefer calling Spring Boot directly from browser/server
```

Use a **stepper / wizard** in `(planner)/trips/[tripId]/layout.tsx` to guide users
through C1→C5 without forcing a linear lock (user can jump back to brief or chat).

#### 4.2.4 Directory layout (Option A — LOCKED)

```
apps/frontend/src/
├── app/                          # routes ONLY — thin pages (§4.2.3)
├── features/
│   ├── intake/                   # C1
│   │   ├── components/           #   trip-brief-form.tsx, destination-picker.tsx
│   │   ├── hooks/                #   use-trip-brief.ts, use-save-brief.ts
│   │   ├── schemas/              #   trip-brief.schema.ts
│   │   ├── types.ts              #   TripBriefFormValues interface
│   │   └── index.ts              #   public exports
│   ├── research/                 # C2
│   ├── itinerary/                # C3
│   ├── booking/                  # C4
│   └── chat/                     # C5
├── components/
│   ├── ui/                       # money-display.tsx, empty-state.tsx
│   └── layout/                   # page-shell.tsx, app-shell.tsx, trip-stepper.tsx
├── lib/
│   ├── api/                      # research-api.ts, trip-api.ts
│   ├── query/                    # query-keys.ts, client.ts
│   ├── i18n/                     # request.ts, routing config (§4.2.10)
│   └── utils/                    # cn.ts, format-money.ts
├── styles/                       # design-tokens.ts, ant-theme.ts, globals.css (§4.2.9)
├── tailwind.config.ts            # at apps/frontend/ root
├── locales/                      # translation files — snake_case (§4.2.10)
│   ├── en/
│   │   ├── common.json
│   │   ├── trip_brief.json
│   │   └── booking_flow.json
│   └── ms/
│       └── ...
├── generated/
│   └── api/
├── hooks/                        # cross-feature: use-trip-context.ts
├── lib/algorithms/               # UI-only sort/filter helpers (§4.0.3)
└── types/
```

#### 4.2.5 Feature module innards

Each `features/<name>/` follows the same internal shape:

```
features/research/
├── components/
│   ├── research-panel.tsx        # exports ResearchPanel
│   ├── recommendation-card.tsx
│   └── research-loading-state.tsx
├── hooks/
│   ├── use-research-query.ts
│   └── use-run-research-mutation.ts
├── schemas/
│   └── research-filters.schema.ts
├── types.ts                      # ResearchPanelProps, ResearchFormValues interfaces
└── index.ts
```

**Form pattern (Ant Design + onValuesChange):**
```tsx
// features/intake/components/trip-brief-form.tsx
const t = useTranslations('trip_brief');
<Form form={form} onValuesChange={(_, all) => onBriefChange(all)} layout="vertical">
  <Form.Item name="destinations" label={t('destinations_label')}>...</Form.Item>
  <Form.Item name="budget" label={t('budget_label')}>...</Form.Item>
</Form>
```
Parent holds debounced save via `useSaveBrief` mutation — form does not call API directly.

**Data flow:**
```
Page → Feature component → hook (React Query) → lib/api/research-api.ts → generated client → Spring Boot
                ↑                                                              ↓
           Ant Design UI                                               zod validate response
```

#### 4.2.6 Frontend coding rules (LOCKED)

All frontend code — human-written or AI-generated — MUST follow these rules.
Violations are fixed before merge, not deferred.

##### A. TypeScript

| Rule | Detail |
|---|---|
| **Strict mode** | `strict: true` in `tsconfig.json` — no exceptions |
| **No `any`** | Use `unknown` + narrow, or generated types. ESLint `@typescript-eslint/no-explicit-any: error` |
| **No non-null assertion abuse** | Avoid `!` unless preceded by an explicit guard |
| **Generated types for API** | Import DTOs from `@/generated/api` only — never redefine `TripBrief`, `Money`, etc. |
| **UI-only types** | View-model shapes (e.g. form draft state) live in `features/*/types.ts` or `types/` — must not mirror OpenAPI schemas field-for-field |
| **Enums** | Use generated union types from OpenAPI; do not duplicate backend enums manually |

##### B. Naming & files

| Kind | Convention | Example |
|---|---|---|
| **Functions / hooks** | **camelCase** | `fetchResearch()`, `useResearchQuery()` |
| **React components** | PascalCase (JSX tag) | `ResearchPanel` in `research-panel.tsx` |
| **All files** | **kebab-case** | `research-panel.tsx`, `use-research-query.ts`, `research-api.ts` |
| Zod schema file | kebab-case + `.schema.ts` | `trip-brief.schema.ts` |
| Props / form interfaces | PascalCase + `Props` / `FormValues` | `ResearchPanelProps`, `TripBriefFormValues` |
| Feature public API | `features/<name>/index.ts` | re-exports only |
| Query keys | camelCase in factory | `lib/query/query-keys.ts` → `queryKeys.research(tripId)` |
| Routes / URLs | **kebab-case** | `/trips/[tripId]/ranked-recommendations` |
| Translation files | **snake_case** | `locales/en/trip_brief.json` |
| Translation keys | **snake_case** | `t('trip_brief.destinations_label')` |

One component per file. One hook per file. File name kebab-case; exported symbol camelCase or PascalCase.

##### B2. Function parameters (≤3 — LOCKED)

Same rule as backend §4.0.4 — **max 3 parameters**. Use interfaces to bundle fields.

| Case | Pattern |
|---|---|
| API call | `fetchResearch(request: FetchResearchRequest)` — 1 param |
| Hook | `useResearchQuery(params: ResearchQueryParams)` — 1 param |
| Component | `ResearchPanel(props: ResearchPanelProps)` or destructured — 1 param |
| Mutation handler | `(command: RunResearchCommand, user: UserContext)` — 2 params max |

Every component with props **must** declare `interface XxxProps`. Every form **must** declare
`interface XxxFormValues`. Map form values → API request in the hook, not inline.

```tsx
// features/intake/types.ts
export interface TripBriefFormProps {
  initialValues?: TripBriefFormValues;
  onValuesChange: (values: TripBriefFormValues) => void;
}

export interface TripBriefFormValues {
  destinations: string[];
  budget: number;
}

// features/research/types.ts
export interface ResearchPanelProps {
  tripId: string;
}

export interface FetchResearchRequest {
  tripId: string;
  locale: string;
}

// features/research/components/research-panel.tsx
export function ResearchPanel({ tripId }: ResearchPanelProps) {
  const t = useTranslations('research');
  return <h1>{t('title')}</h1>;
}
```

##### B3. Human-readable code

Same principles as backend §4.0.2-B3 / §4.0.4 — name by intent, early returns, no magic
strings (use i18n keys or constants), ~40 lines max per function, one abstraction level.

##### C. Imports & module boundaries

```
✅  app/.../page.tsx  →  features/research          (via index.ts)
✅  features/research →  lib/api, components/ui, generated/api
✅  features/research →  features/intake            ❌ NEVER — use shared components/
✅  lib/api           →  generated/api              only
❌  components/ui     →  features/*                 NEVER
❌  generated/api      →  anything edited manually    NEVER
```

- Use path alias `@/` → `src/`.
- Cross-feature reuse goes through `components/ui/` or `components/layout/` — extract
  shared UI there, do not import across `features/`.
- Every feature exposes a **public surface** via `index.ts`; internal files (`components/X.tsx`)
  are not imported from outside the feature.

##### D. Pages (`app/**/page.tsx`)

| Rule | Detail |
|---|---|
| **Max ~20 lines** | Import one feature panel/component; pass route params as props |
| **No `'use client'` unless unavoidable** | Prefer client boundary inside `features/` |
| **No data fetching** | No `fetch`, no React Query, no `useEffect` for API calls |
| **No form state** | No `Form.useForm()` in pages |
| **Metadata** | `export const metadata` or `generateMetadata` allowed in server pages |

```tsx
// ✅ app/(planner)/trips/[tripId]/research/page.tsx
import { ResearchPanel } from '@/features/research';

type Props = { params: Promise<{ tripId: string }> };

export default async function ResearchPage({ params }: Props) {
  const { tripId } = await params;
  return <ResearchPanel tripId={tripId} />;
}
```

##### E. Feature components (`features/*/components/`)

| Rule | Detail |
|---|---|
| **Screen shell** | One `*-panel.tsx` or `*-view.tsx` per feature — entry point used by the page |
| **`'use client'`** | Required when using hooks, Ant Design interactivity, or browser APIs |
| **Presentational split** | Dumb display components receive typed props; data hooks live in parent shell or custom hooks |
| **No direct API calls** | Components call hooks — never import `lib/api` directly except in hooks |
| **Loading / error / empty** | Every data-driven screen handles all three states explicitly |
| **Ant Design** | Use Ant Design components; wrap in `components/ui/` only when reused ≥2 times |

##### F. Forms (Ant Design — mandatory pattern)

All forms use Ant Design `Form` with **`onValuesChange`** — not uncontrolled inputs,
not raw `useState` per field.

```tsx
// features/intake/components/trip-brief-form.tsx
'use client';

import { Form } from 'antd';
import { useTranslations } from 'next-intl';
import type { TripBriefFormProps, TripBriefFormValues } from '../types';

export function TripBriefForm({ initialValues, onValuesChange }: TripBriefFormProps) {
  const t = useTranslations('trip_brief');
  const [form] = Form.useForm<TripBriefFormValues>();

  return (
    <Form
      form={form}
      layout="vertical"
      initialValues={initialValues}
      onValuesChange={(_, allValues) => onValuesChange(allValues)}
    >
      <Form.Item name="destinations" label={t('destinations_label')}>
        {/* fields */}
      </Form.Item>
    </Form>
  );
}
```

| Rule | Detail |
|---|---|
| **Save trigger** | Parent feature hook debounces `onValuesChange` → mutation (`useSaveBrief`) |
| **Submit** | `onFinish` only for explicit submit actions (e.g. "Start research"), not auto-save |
| **Validation** | Ant Design rules for UX; zod schema in `schemas/` for shape check before API call |
| **API mapping** | Map form values → generated request type in the hook, not in the form component |
| **No API in form** | Form components emit values upward — zero fetch/mutation inside form files |

##### G. Hooks & data fetching (`features/*/hooks/`, `lib/query/`)

| Rule | Detail |
|---|---|
| **React Query for all server state** | Queries + mutations — no manual `useState` + `useEffect` fetch |
| **Query keys** | Defined in `lib/query/query-keys.ts` — no inline string arrays |
| **One concern per hook** | `useResearchQuery` (read) separate from `useRunResearchMutation` (write) |
| **API access** | Hooks call `lib/api/<resource>-api.ts` — the only layer that touches generated client |
| **Zod at boundary** | Validate API response with zod in `lib/api/` before returning to hook |
| **Error mapping** | Map `ApiErrorResponse.code` → i18n key in `lib/api/` — hooks display translated message |
| **Stale time** | Default `60s` queries; `0` for polling screens (research/chat) — set in `lib/query/client.ts` |
| **Retry** | 2 retries on network error; **no retry** on 4xx |
| **Debounce** | Form auto-save: **300ms** debounce on `onValuesChange` before mutation |
| **Optimistic updates** | Only in mutations where rollback is safe; booking (C4) — **never** optimistic |

```tsx
// lib/query/query-keys.ts
export const queryKeys = {
  trips: {
    all: ['trips'] as const,
    detail: (tripId: string) => ['trips', tripId] as const,
  },
  research: {
    result: (tripId: string) => ['research', tripId] as const,
  },
};
```

##### H. API layer (`lib/api/`)

| Rule | Detail |
|---|---|
| **Single entry** | `lib/api/client.ts` — base URL, auth header, `X-Request-Id`, error parsing |
| **Per-resource files** | `trip-api.ts`, `research-api.ts`, … — kebab-case; one function per endpoint |
| **Generated client only** | Wrap generated functions; base path `/api/v1` |
| **No React** | Pure async functions — zero hooks, zero JSX |
| **Return typed data** | Parse with zod; throw typed `ApiError` with `code` on failure |
| **Error envelope** | Parse `{ code, message, details }` from §6.1 — map `code` to i18n |

```tsx
// lib/api/research-api.ts — 1 param; camelCase function; kebab-case file
import { getRankedRecommendations } from '@/generated/api';
import { rankedRecommendationsSchema } from './schemas/research.schema';

export async function fetchResearch(request: FetchResearchRequest) {
  const raw = await getRankedRecommendations({ path: { tripId: request.tripId } });
  return rankedRecommendationsSchema.parse(raw);
}
```

##### I. Styling (Tailwind + Ant Design)

| Rule | Detail |
|---|---|
| **Tailwind first** | Layout, spacing, typography, colors via **Tailwind utility classes** (§4.2.9) |
| **Ant for components** | Forms, tables, modals, buttons — Ant Design behavior; style via Tailwind + tokens |
| **Unified tokens** | Colors/spacing/radius from `design-tokens.ts` → `tailwind.config.ts` + `ant-theme.ts` — **never one-off hex/rpx in components** |
| **Ant overrides** | Default Ant look overridden globally via `@layer components` in `globals.css` |
| **Ant `classNames`** | Per-instance tweaks via Ant Design 5 `classNames` / `styles` props + Tailwind (`cn()`) |
| **No CSS Modules** | Use Tailwind instead — do not add new `*.module.css` files |
| **No inline styles** | Except truly dynamic values (e.g. `width: ${pct}%`) |
| **`cn()` helper** | `lib/utils/cn.ts` — `clsx` + `tailwind-merge` for conditional classes |

See **§4.2.9** for the full styling stack and unified design patterns.

##### J. State management

| State type | Where |
|---|---|
| Server / API data | React Query (hooks) |
| Form draft | Ant Design `Form` instance — lifted to feature shell if shared |
| UI ephemeral (modal open, tab) | `useState` in component |
| Trip context (current tripId, step) | `hooks/use-trip-context.ts` or layout provider |
| Global | React Context sparingly — layout providers only; **no Redux** in v1 |

##### J2. Exports, imports & file conventions

| Rule | Detail |
|---|---|
| **Named exports** | Prefer named exports in features/components; **default export** only for `app/**/page.tsx` |
| **Import order** | 1) external libs 2) `@/` absolute 3) relative — blank line between groups |
| **Barrel `index.ts`** | Re-export public API only — no logic; avoid circular barrels |
| **Test files** | Co-located `*.test.ts` / `*.test.tsx` (e.g. `research-api.test.ts`) |
| **Env files** | `.env.local` (gitignored), `.env.example` (committed template) — document all keys |
| **Required env** | `NEXT_PUBLIC_API_BASE_URL` → backend `/api/v1` |

##### K. Error, loading, and empty states

Every feature screen MUST implement:

```tsx
if (isLoading) return <ResearchLoadingState />;
if (isError) return <ErrorAlert error={error} onRetry={refetch} />;
if (!data?.items.length) return <EmptyState message={t('no_recommendations')} />;
return <RecommendationList items={data.items} />;
```

- Use shared `components/ui/error-alert.tsx` and `components/ui/empty-state.tsx`.
- Next.js **`loading.tsx`** and **`error.tsx`** per route group where SSR applies.
- Booking errors (C4) show translated message from `ApiError.code` — never generic text alone.
- Retry must re-call the query/mutation, not reload the page.

##### K2. Streaming, a11y & security (frontend)

| Rule | Detail |
|---|---|
| **C5 chat stream** | Dedicated hook in `features/chat/hooks/use-chat-stream.ts` — SSE from backend |
| **Markdown render** | Sanitize LLM HTML (DOMPurify) before render — prevent XSS |
| **a11y** | Ant Design components + `aria-label={t('...')}` on icon-only buttons |
| **Ant Design locale** | Sync `ConfigProvider locale` with next-intl active locale |
| **Auth header** | Attach session/JWT in `lib/api/client.ts` — all requests user-scoped (§4.0.5) |

##### L. Security & env

- **No secrets** in frontend — only `NEXT_PUBLIC_*` vars.
- **No API keys** for LLM or suppliers in the browser — all AI calls go through Spring Boot.
- **Booking confirm (C4):** explicit labeled button; disabled while pending; idempotency key
  from backend echoed on retry (see §7).

##### M. Testing (frontend)

| Target | Tool | Rule |
|---|---|---|
| Zod schemas | Vitest | Validate sample payloads + reject bad shapes |
| `lib/api/` functions | Vitest + MSW | Mock HTTP; assert zod parse |
| Hooks | `@testing-library/react` | Wrap in QueryClientProvider |
| Components | RTL | Test loading/error/empty/render paths |
| E2E (Phase 2+) | Playwright | intake → research → itinerary |

##### N. AI-generated frontend code — extra rules

1. **Place code in the correct feature** — C2 code goes in `features/research/`, not `app/`.
2. **Never create `services/` folder** — API functions go in `lib/api/`.
3. **Never skip the hook layer** — page → component → hook → api, always.
4. **Never hand-write API types** — run codegen if a type is missing.
5. **Forms always use `onValuesChange`** — reject diffs that use uncontrolled inputs or per-field `useState`.
6. **Export via `index.ts`** — new public components/hooks must be re-exported.
7. **Self-check against §4.2.2 layer table** before finishing.

#### 4.2.7 Data flow (reference)

```
Page → Feature component → hook (React Query) → lib/api/<resource>-api.ts → /api/v1/ → Spring Boot
                ↑                                                              ↓
           Ant Design UI                                    { code, message, details } → i18n
```

#### 4.2.8 Open questions (frontend — non-blocking)

1. **SSR depth:** trip list/brief SSR; C2–C5 client-heavy — tune per screen in Phase 1.

#### 4.2.9 Styling — Tailwind CSS + Ant Design (LOCKED)

**Ant Design** provides components and interaction patterns (forms, tables, modals).
**Tailwind CSS** is the **default styling layer** — it overrides Ant's visual defaults
and keeps the **whole app on one unified design system**.

Goal: one consistent look (spacing, color, type, radius, shadows) everywhere — planner
screens, admin panel, marketing — without mixing ad-hoc CSS approaches.

##### Stack (LOCKED)

| Layer | Tool | Role |
|---|---|---|
| **Design tokens (source of truth)** | `styles/design-tokens.ts` | Brand colors, spacing, radius, font, shadows |
| **Utility styling** | **Tailwind CSS** | Layout, spacing, typography, responsive, dark mode |
| **Component library** | **Ant Design 5** | Form, Table, Modal, Button, Layout — behavior + a11y |
| **Ant token sync** | `ConfigProvider` + `cssVar` | Map same tokens → Ant so Ant internals stay aligned |
| **Global Ant overrides** | `globals.css` `@layer components` | Tailwind `@apply` on `.ant-*` — **default Ant look overridden** |
| **Conditional classes** | `cn()` (`clsx` + `tailwind-merge`) | Compose Tailwind on Ant components |

**Rejected:** CSS Modules (use Tailwind), styled-components/Emotion, Sass/Less.

##### Override hierarchy

```
1. design-tokens.ts          ← single source of truth (colors, spacing, radius, fonts)
2. tailwind.config.ts        ← exposes tokens as Tailwind theme
3. ant-theme.ts              ← same tokens → ConfigProvider (Ant internals)
4. globals.css @layer        ← default Ant component overrides via @apply (Tailwind)
5. Tailwind classes          ← on wrappers, layouts, custom UI
6. Ant classNames/styles     ← per-component instance tweaks
```

##### Directory layout

```
apps/frontend/
├── tailwind.config.ts
├── postcss.config.js
└── src/
    ├── styles/
    │   ├── design-tokens.ts      # SINGLE SOURCE — colors, spacing, radius, typography
    │   ├── ant-theme.ts          # tokens → ConfigProvider theme
    │   └── globals.css           # @tailwind + @layer ant overrides
    ├── lib/utils/cn.ts           # clsx + tailwind-merge
    └── app/
        ├── globals.css           # imports styles/globals.css
        └── providers.tsx         # ConfigProvider + theme
```

##### Unified design patterns (mandatory)

All screens follow these patterns — **no one-off layouts**:

| Pattern | Tailwind convention | Used in |
|---|---|---|
| **Page shell** | `mx-auto max-w-7xl px-4 py-6 md:px-6 md:py-8` | Every feature panel |
| **Section gap** | `flex flex-col gap-6` | Vertical stacking within a screen |
| **Card grid** | `grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3` | Recommendations, trips list |
| **Form layout** | `space-y-4` inside Ant `Form layout="vertical"` | C1 brief, admin forms |
| **Primary button** | Ant `<Button type="primary">` + global `@apply` in globals.css | CTAs |
| **Page title** | `text-2xl font-semibold text-foreground` | Panel headers |
| **Muted text** | `text-sm text-muted-foreground` | Hints, secondary info |
| **Error alert** | `rounded-lg border border-destructive/50 bg-destructive/10 p-4` | Error states |

Shared layout components in `components/layout/` encode these patterns once
(`page-shell.tsx`, `page-header.tsx`) — features compose them, do not reinvent.

##### Token sync example

```typescript
// styles/design-tokens.ts — single source
export const tokens = {
  color: { primary: '#1677ff', foreground: '#0f172a', muted: '#64748b', destructive: '#dc2626' },
  radius: { sm: '0.375rem', md: '0.5rem', lg: '0.75rem' },
  spacing: { page: '1.5rem', section: '1.5rem' },
  font: { sans: 'Inter, system-ui, sans-serif' },
} as const;
```

```typescript
// tailwind.config.ts — mirrors design-tokens
import { tokens } from './src/styles/design-tokens';
export default {
  theme: {
    extend: {
      colors: {
        primary: tokens.color.primary,
        foreground: tokens.color.foreground,
        muted: tokens.color.muted,
        destructive: tokens.color.destructive,
      },
      borderRadius: { sm: tokens.radius.sm, md: tokens.radius.md, lg: tokens.radius.lg },
      fontFamily: { sans: [tokens.font.sans] },
    },
  },
};
```

```typescript
// styles/ant-theme.ts — same tokens → Ant
import { tokens } from './design-tokens';
export const antTheme = {
  cssVar: true,
  token: {
    colorPrimary: tokens.color.primary,
    borderRadius: Number.parseFloat(tokens.radius.md) * 16,
    fontFamily: tokens.font.sans,
  },
};
```

##### Default Ant overrides via Tailwind (globals.css)

Global overrides apply **by default** to all Ant components — feature code should not
repeat base styling:

```css
/* styles/globals.css */
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer components {
  /* Override Ant Button defaults app-wide */
  .ant-btn-primary {
    @apply rounded-md font-semibold shadow-sm;
  }
  .ant-card {
    @apply rounded-lg border-border shadow-sm;
  }
  .ant-form-item-label > label {
    @apply text-sm font-medium text-foreground;
  }
  .ant-input, .ant-select-selector {
    @apply rounded-md;
  }
}
```

##### Component usage

```tsx
// features/research/components/research-panel.tsx
import { cn } from '@/lib/utils/cn';
import { PageShell } from '@/components/layout/page-shell';

export function ResearchPanel({ tripId }: ResearchPanelProps) {
  return (
    <PageShell>
      <h1 className="text-2xl font-semibold text-foreground">{t('title')}</h1>
      <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card classNames={{ body: cn('p-4') }}>...</Card>
      </div>
    </PageShell>
  );
}
```

##### Styling coding rules

| Rule | Detail |
|---|---|
| **One design system** | All colors/spacing from tokens — no random `text-[#333]` or `p-[13px]` |
| **Tailwind for layout** | Flex/grid/gap/padding/margin — always Tailwind classes |
| **Ant for widgets** | Input, Select, DatePicker, Table — Ant components, Tailwind wrappers |
| **Global Ant overrides first** | Add to `globals.css @layer components` before per-component hacks |
| **Use `cn()`** | Merge Tailwind classes; resolve conflicts with `tailwind-merge` |
| **Dark mode** | Tailwind `dark:` variants + Ant `darkAlgorithm` — both wired to same tokens |
| **No new CSS Modules** | Tailwind replaces CSS Modules project-wide |
| **No styled-components** | Tailwind only |
| **Responsive** | Mobile-first — `sm:` `md:` `lg:` breakpoints consistently |
| **i18n + styling** | Tailwind for layout only — never hardcode user-visible text in classes |

##### Phase 0 setup

Install and configure in scaffold: `tailwindcss`, `postcss`, `autoprefixer`, `clsx`,
`tailwind-merge`; wire `design-tokens.ts` → config + Ant theme; seed `globals.css`
with base Ant overrides.

#### 4.2.10 Internationalization — i18n (LOCKED)

All **user-facing strings** go through **next-intl**. No hardcoded labels, buttons,
errors, or placeholders in components.

##### Setup

| Item | Choice |
|---|---|
| Library | **next-intl** (App Router) |
| Message files | `src/locales/{locale}/` — **snake_case** file names |
| Keys | **snake_case** — `trip_brief.destinations_label` |
| Default locale | `en` |
| v1 locales | `en` + one secondary (e.g. `ms`) — add more without code changes |

##### File layout & naming

```
locales/
├── en/
│   ├── common.json           # shared: buttons, errors, nav — include errors.trip_not_found etc.
│   ├── trip_brief.json       # C1
│   ├── research.json         # C2
│   ├── itinerary.json        # C3
│   ├── booking_flow.json     # C4
│   └── chat.json             # C5
└── ms/
    ├── common.json
    └── trip_brief.json
```

```json
// locales/en/trip_brief.json — keys are snake_case
{
  "destinations_label": "Where do you want to go?",
  "budget_label": "Budget",
  "save_success": "Trip brief saved"
}
```

##### Usage rules

| Rule | Detail |
|---|---|
| **No hardcoded UI text** | All labels, titles, errors, empty states use `t('key')` |
| **Namespace per feature** | `useTranslations('trip_brief')` in C1, `'research'` in C2, etc. |
| **API errors** | Map backend error codes → i18n keys in hook — display translated message |
| **Ant Design Form labels** | `label={t('destinations_label')}` — not string literals |
| **Locale in requests** | Pass `locale` inside `FetchResearchRequest` (1 DTO field) — not a 4th function param |
| **Dates / money** | Format with `Intl` + locale from next-intl — not hardcoded formats |
| **Do not translate** | Log messages, dev-only text, API field names |

```tsx
// ✅
const t = useTranslations('trip_brief');
<Form.Item name="destinations" label={t('destinations_label')}>

// ❌
<Form.Item name="destinations" label="Where do you want to go?">
```

##### i18n coding rules

- Translation files: **snake_case** names only (`booking_flow.json`, not `bookingFlow.json`).
- Keys: **snake_case** only — `save_success`, not `saveSuccess`.
- New feature → add matching namespace file under each locale folder.
- Missing key fails CI lint (eslint-plugin-i18next or custom check in Phase 0).

---

## 5. AI abstraction (LangChain4j hidden behind our interfaces)

Even though LangChain4j is chosen, features depend on **our** interfaces, so a future
swap or multi-provider setup costs one adapter package.

### 5.1 Provider-agnostic interfaces
```java
public interface LlmClient {
    String complete(Prompt p, LlmOptions o);
    <T> T completeStructured(Prompt p, Class<T> type, LlmOptions o);   // strict typing
    ToolResult completeWithTools(Prompt p, List<ToolSpec> tools, LlmOptions o);
    Flux<String> stream(Prompt p, LlmOptions o);
}
public interface EmbeddingClient { float[] embed(String t); List<float[]> embedBatch(List<String> t); }
public interface VectorStore { void upsert(String id, float[] v, Map<String,Object> md);
                               List<Match> search(float[] q, int k, Filter f); }
```

### 5.2 Reusable building blocks
`StructuredOutputRunner` · `Retriever` (RAG) · `ToolRegistry` · `ConversationMemory`
· `Summarizer` — each feature composes these.

### 5.3 Cross-cutting
`PromptTemplateStore` (versioned, per-provider variants) · `Guardrails` (schema +
business-rule validation) · `TokenBudget` · `SemanticCache` (Redis) ·
`Observability` (tokens, latency, provider, cost per call → `ai_call_log`).

### 5.4 Provider switching (Anthropic ↔ OpenAI)
`LlmClientRouter implements LlmClient` picks the active provider from config;
callers still just inject `LlmClient`.

```yaml
ai:
  provider:
    default: anthropic        # chat default
    embeddings: openai        # embeddings PINNED to one provider (see note)
  routing:                    # optional per-feature override
    research: anthropic
    nl-search: openai
  anthropic: { api-key: ${ANTHROPIC_API_KEY}, model: claude-... }
  openai:    { api-key: ${OPENAI_API_KEY},    model: gpt-... }
```
- Keep `LlmOptions` provider-neutral; map to vendor params inside each adapter.
- Tool + structured-output schemas are declared once (our types), translated per provider.
- **Embeddings provider is pinned per vector index** — Anthropic and OpenAI vectors
  are not comparable; mixing corrupts search. Chat may switch freely; embeddings may not.

---

## 6. Strict typing strategy (end-to-end)

One contract, enforced at every boundary:

1. **Domain:** value objects (`Money`, `DateRange`, `GeoLocation`), enums for fixed
   sets, invariants in constructors. No primitive obsession, no nullable-by-default.
2. **API contract:** **OpenAPI spec is the source of truth** → generate the Next.js
   TypeScript client (`openapi-typescript`/`orval`). Frontend and backend cannot drift.
3. **LLM I/O:** JSON Schema derived from the same DTOs; LangChain4j binds model output
   to typed objects; `Guardrails` validates before anything is persisted or acted on.
4. **DB:** Flyway migrations, `NOT NULL` by default, `numeric` for money, `timestamptz`
   for time, enums as constrained types. No implicit conversions.
5. **Frontend:** TypeScript `strict: true`; only the generated types cross the wire;
   runtime validation (zod) on responses at the edge; feature-module layering (§4.2).

### 6.1 API contract & HTTP conventions (LOCKED)

#### Versioning

- All REST endpoints under **`/api/v1/`** — e.g. `/api/v1/trips`, `/api/v1/trips/{tripId}/ranked-recommendations`.
- Breaking changes → increment to `/api/v2/`; v1 supported until frontend migrates.
- OpenAPI spec tagged with version; codegen includes `/api/v1` base path.

#### HTTP verbs — CRUD only (no PATCH)

| Operation | Verb | Example |
|---|---|---|
| Read one / list | **GET** | `GET /api/v1/trips/{tripId}` |
| Create | **POST** | `POST /api/v1/trips` |
| Full replace / update | **PUT** | `PUT /api/v1/trips/{tripId}/brief` |
| Delete | **DELETE** | `DELETE /api/v1/trips/{tripId}` |

- **Do not use PATCH** — partial updates use **PUT** with full resource body, or a dedicated
  POST action endpoint (e.g. `POST /api/v1/trips/{tripId}/research/run`).
- URL segments: **kebab-case** — `ranked-recommendations`, `price-history`.
- Resource names: **plural nouns** — `/trips`, `/bookings`.

#### Standard error envelope

Every non-2xx response returns the same shape:

```json
{
  "code": "trip_not_found",
  "message": "Human-readable fallback (English)",
  "details": {
    "trip_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

| Field | Rule |
|---|---|
| **`code`** | **snake_case** machine identifier — registered in OpenAPI + error catalog |
| **`message`** | English fallback for logs/dev; **frontend maps `code` → i18n key** for display |
| **`details`** | Optional structured context — field errors, ids, expiry times; no stack traces |

```java
// api/dto/error/ApiErrorResponse.java
public record ApiErrorResponse(
    @NotNull String code,
    String message,
    Map<String, Object> details
) {}

// domain/exception/TripNotFoundException.java
public class TripNotFoundException extends DomainException {
    public TripNotFoundException(UUID tripId) {
        super("trip_not_found", "Trip not found", Map.of("trip_id", tripId.toString()));
    }
}
```

Frontend: `lib/api/` maps `ApiErrorResponse.code` → i18n key in `common.errors.{code}`.

#### Pagination (list endpoints)

Query params on GET list endpoints:

| Param | Type | Default |
|---|---|---|
| `page` | int (0-based) | `0` |
| `page_size` | int | `20` (max `100`) |
| `sort` | string | `-created_at` (prefix `-` = desc) |

Response wrapper:

```json
{
  "items": [ ... ],
  "page": 0,
  "page_size": 20,
  "total": 142
}
```

#### Idempotency

- Booking confirm: client sends **`Idempotency-Key: {uuid}`** header.
- Backend deduplicates within 24h — returns same result on retry.

---

## 7. Booking quick actions (C4) — design & safety

Booking touches money, so it is a **strict, human-in-the-loop state machine** — the
LLM prepares, the **user** commits.

```
DRAFT ─(search/quote)→ QUOTED ─(optional hold)→ HELD ─(USER confirms)→ CONFIRMED
                                                   └─(timeout/decline)→ CANCELLED
                              any step ↘ FAILED (with typed reason)
```

- The agent may **assemble a booking proposal** (flight/hotel + price + terms), but
  **confirmation is an explicit user action in the UI** — never auto-executed by the LLM.
- **Payment is delegated to a payment/booking provider** via tokenized flow or redirect.
  The system stores provider references and tokens only — **never raw card/PAN data**.
- **Idempotency keys** on confirm to prevent double-booking; every state transition
  is persisted and audited.
- Price re-validation at confirm time (quotes expire); user sees final price before commit.
- Full cancellation/refund status tracked; no silent side effects.

---

## 8. Data model sketch

- **User:** `user` — `id`, `username` (unique), `password_hash`, `email`, `role` (`USER` | `ADMIN`), `enabled`, audit columns (§4.0.5, §4.0.6)
- **Seed:** dev admin row — username `ADMIN`, BCrypt(`123456`), role `ADMIN` (§4.0.6)
- **Audit:** `audit_event` — admin actions (reset password, enable/disable user)
- Core: `trip` (FK `user_id`), `trip_brief`, `itinerary_day`, `itinerary_item`
- Booking: `booking` (+ `booking_status` history), `payment_reference`
- Chat: `conversation`, `message`
- Knowledge/RAG: `destination`, `destination_embedding` (pgvector), `poi`
- History: `price_history`, `seasonality`  (the "historical data" backing C2)
- Ops: `ai_call_log` (tokens/cost/provider)

---

## 9. Cross-cutting engineering

- **Testing AI:** mock `LlmClient` for unit tests; golden-file tests for structured
  output; schema contract tests; small **eval harness** in CI on prompt changes.
- **Algorithms:** introduce DSA per §4.0.3 when correctness, scale, or optimization
  requires it; measure before optimizing; pure logic in `domain/algorithm/`.
- **Resilience:** timeouts, retries+backoff, circuit breaker per provider/supplier;
  typed fallbacks (never a hallucinated result).
- **Security:** secrets in env/secret manager; treat all external/LLM output as
  untrusted; rate-limit AI + booking endpoints; audit money-touching actions;
  sanitize user input before LLM prompts (injection guardrails in `ai/` Guardrails).
- **Config:** model, temperature, token caps, routing per feature in `application.yml`.
- **Observability:** `X-Request-Id` on all requests; structured logs; no PII in logs (§4.0.2-J2).
- **API standards:** versioned `/api/v1/`, CRUD verbs, standard error envelope (§6.1).
- **Auth:** user-scoped data access; no tenant (§4.0.5).
- **CI gates:** see §15 — compile, test, codegen drift, Flyway validate must pass before merge.
- **Branch naming:** `feature/{ticket}-short-desc`, `fix/{ticket}-short-desc`.
- **Prompt changes:** any edit under `ai/prompt/` triggers eval harness in CI.

---

## 10. Phased roadmap & delivery

Phases map to **epics** in [`plans/BACKLOG.md`](../BACKLOG.md). Each phase has explicit
**exit criteria** — do not start the next phase until the current one passes.

### Phase 0a — DevEx & runtime (Sprint 0)

**Goal:** clone → `npm run prereq` → `docker compose up` → healthy stack.

| Deliverable | Detail |
|---|---|
| Prereq scripts | `scripts/check-prerequisites.*`, `npm run prereq` |
| Docker | `docker-compose.yml`, `docker-compose.dev.yml`, Dockerfiles, `.env.example` |
| Health | `GET /api/v1/health`, `GET /api/v1/ready`; `wait-for-services.sh` |
| CI skeleton | Node 22 + Java 21 + Docker build (§15) |
| PR template | §12.3 checklist embedded |

**Exit criteria:** all prereq checks green; `docker compose up --build` reaches healthy;
CI pipeline runs on PR.

### Phase 0b — Platform skeleton (Sprints 1–2)

**Goal:** authenticated API + generated frontend client + admin + AI router shell.

**Sprint 1 — backend platform**

| Deliverable | Detail |
|---|---|
| Gradle + Spring Boot | Package layout (§4), profiles `dev`/`docker`/`prod` |
| Flyway + Postgres | `V1__create_user_table.sql`; pgvector enabled |
| Domain VOs | `Money`, `DateRange`, `UserContext` + unit tests |
| Error envelope | `DomainException`, `@ControllerAdvice`, `ApiErrorResponse` (§6.1) |
| OpenAPI bootstrap | Spec in `api/openapi/`; health + auth + trip stub paths |
| Auth (JWT cookie) | Login/logout/me endpoints (§4.0.5) |
| MapStruct | Mapper config + example controller→response flow |
| Trip scaffold | `GET/POST /api/v1/trips` — thin controller, service unit test |

**Sprint 2 — AI + frontend platform**

| Deliverable | Detail |
|---|---|
| Admin | Seed `ADMIN`/`123456` (dev only); admin APIs + audit (§4.0.6) |
| AI platform | `LlmClient`/`EmbeddingClient`/`VectorStore` ports + `LlmClientRouter` |
| LangChain4j | Anthropic + OpenAI adapters in `ai/langchain4j/` only |
| AI observability | `ai_call_log`, `X-Request-Id` MDC, token/latency logging |
| Next.js scaffold | App Router, `(planner)/` + `(admin)/` shells, trip stepper placeholder |
| Design system | Tailwind + tokens + Ant overrides + `PageShell` (§4.2.9) |
| i18n | next-intl, `en/` + `ms/` namespaces (§4.2.10) |
| Codegen | OpenAPI → TS; `npm run codegen`; CI drift check (§15) |
| API client | `lib/api/client.ts`, zod boundary, React Query setup |

**Phase 0 exit criteria (gate before Phase 1):**
- [ ] §12.3 checklist passes on a sample PR
- [ ] Login as `ADMIN` works in docker profile; seed skipped in prod profile
- [ ] Frontend calls backend with generated types (no hand-written API DTOs)
- [ ] `LlmClientRouter` smoke test with config switch anthropic ↔ openai
- [ ] OpenAPI codegen drift fails CI when spec changes without regen

### Phase 1 — Core loop (Sprints 3–5)

| Sprint | Feature | Outcome |
|---|---|---|
| 3 | **C1** Intake | `TripBrief` create/edit E2E with structured LLM extraction |
| 4 | **C2** Research | Agent + **stub tools** → typed `RankedRecommendations` |
| 5 | **C3** Itinerary | Day-by-day plan; intake → research → itinerary E2E test |

**Phase 1 exit criteria:**
- [ ] Happy path: create trip → brief → research → itinerary without manual DB edits
- [ ] Each feature has service unit tests + frontend loading/error/empty states
- [ ] Eval harness v0 runs in CI on prompt template changes (C2)

### Phase 2 — Action + refine (Sprints 6–8)

| Sprint | Feature | Outcome |
|---|---|---|
| 6 | **C4** search/quote | Booking state machine + stub suppliers; browse UI |
| 7 | **C4** confirm | Idempotency, payment provider, audit trail (§7) |
| 8 | **C5** Chat | SSE refinement + itinerary patch tool; Redis semantic cache (profile `cache`) |

**Phase 2 exit criteria:**
- [ ] Booking confirm is human-gated; no LLM auto-book
- [ ] `Idempotency-Key` dedup verified in integration test
- [ ] Chat markdown sanitized (DOMPurify)

### Phase 3 — Later features

Packing, review summaries, disruption replanning, narrative, translation, groups.
(See §3 "Later" — no sprint allocation until Phase 2 exits.)

---

## 11. Open questions & locked decisions

### Locked (resolved — do not re-open without ADR)

| Topic | Decision | ADR / section |
|---|---|---|
| Build tool | **Gradle** (Kotlin DSL), wrapper committed | `docs/adr/001-gradle.md` |
| Auth v1 | **Self-issued JWT** in httpOnly cookie | `docs/adr/002-jwt-auth.md`, §4.0.5 |
| External APIs | **Stub-first** — features never blocked on vendor | §4.0.7 |
| Guest vs accounts | User accounts from v1, no tenant | §4.0.5 |
| BFF vs direct | Direct to Spring Boot | §4.0.5 |

### Still open (non-blocking for Phase 0–1)

| # | Question | Blocks | Default until decided |
|---|---|---|---|
| 1 | Web-search API vendor | Live C2 search adapter only | `StubSearchAdapter` (§4.0.7) |
| 2 | Flight/hotel supplier APIs | Live C4 adapters only | `StubFlightSearchAdapter`, `StubHotelSearchAdapter` |
| 3 | Payment provider (e.g. Stripe) | Live payment flow in Sprint 7 | Stub payment port returning fixture quotes |
| 4 | Historical data source | Live `HistoricalPriceTool` data richness | Seed fixture rows in Flyway dev migration |
| 5 | OAuth (Google/GitHub) | Social login only | JWT email/password auth (§4.0.5) |
| 6 | Local model (Ollama) | Optional provider | Router interface stays open (§5.4) |

**Rule:** open questions affect **live adapter** selection only — stub adapters and port
interfaces ship regardless.

---

## 12. Mandatory development workflow (LOCKED)

**Every new feature, bug fix with behavior change, and every block of AI-generated
code MUST follow this workflow.** No shortcuts. If a step is skipped, the change is
not merge-ready.

This applies equally to human developers and AI assistants (Cursor, Copilot, etc.).
Before writing code, the AI/human reads this section and the relevant plan sections.

### 12.1 Workflow overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│  0. PREREQ   → npm run prereq; install missing tools; docker available  │
│  1. PLAN     → scope the change; confirm it fits locked architecture    │
│  2. CONTRACT → define/update OpenAPI + DTOs (API-first)                 │
│  3. DOMAIN   → model + ports (if new concepts)                          │
│  4. SERVICE  → business logic in application/ (NOT in controller)       │
│  5. ADAPTERS → infrastructure / ai implementations of ports           │
│  6. ROUTE    → thin controller + mappers only                           │
│  7. FRONTEND → generated client + UI (Node 22)                          │
│  8. VERIFY   → tests + checklist (§12.3) + layer-boundary review        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 12.2 Step-by-step (do in order)

| Step | Action | Output / gate |
|---|---|---|
| **0 — Prereq** | Run `npm run prereq`. Install any missing tool (Node 22, Java 21, Docker). Verify `./gradlew --version` and `docker compose version`. | All checks green; Docker daemon running |
| **1 — Plan** | Name the feature (maps to C1–C5 or a sub-task). Read §1, §4, §4.0.1, §4.0.4, §4.2, §4.2.6. If the change alters architecture, **stop and update this plan first**. | One-line scope + affected packages listed |
| **2 — Contract** | Add/update OpenAPI paths, request/response schemas, error codes. Run codegen. | OpenAPI diff + regenerated TS client; frontend/backend cannot compile against stale types |
| **3 — Domain** | Add value objects, enums, aggregates, port interfaces in `domain/`. Pure algorithms in `domain/algorithm/` if needed (§4.0.3). Zero framework imports. | New/changed types in `domain/model`, `domain/valueobject`, `domain/port`, optionally `domain/algorithm/` |
| **4 — Service** | Implement use-case in `application/<feature>/`. All business rules, orchestration, and `@Transactional` boundaries here. Inject ports — never concrete adapters. | `*Service` or `*UseCase` class with unit tests (mock ports) |
| **5 — Adapters** | Implement ports in `infrastructure/` or `ai/`. Map JPA ↔ domain. No business rules leaking into adapters beyond mapping. | Adapter + integration test where I/O is involved |
| **6 — Route** | Add `@RestController` method: validate input DTO → call service → map to response DTO. **≤10 lines per endpoint.** | Controller diff contains **no** repos, LLM clients, or domain logic |
| **7 — Frontend** | Add code under `features/<feature>/` per §4.2.6. Page stays thin (§4.2.6-D). Hook → `lib/api/` → generated client. Ant Design Form with `onValuesChange`. Node 22. | Feature module + thin page; passes §4.2.6 checklist |
| **8 — Verify** | Complete §12.3 checklist. Run tests + lint. AI-generated code gets the same review as human code. | All checklist boxes ticked before PR |

### 12.3 Pre-merge checklist (required on every PR)

Copy into PR description; all items must pass:

- [ ] **Prerequisites** — `npm run prereq` passes; Docker stack starts (`docker compose up --build`) if infra touched (§4.0.0)
- [ ] **Plan alignment** — change maps to a locked feature or documented sub-task; no architecture drift
- [ ] **Monorepo** — changes land in correct app (`apps/frontend` / `apps/backend`); root scripts updated if needed
- [ ] **Runtime** — frontend tested on Node 22; backend compiled with Java 21
- [ ] **Contract** — OpenAPI updated; TS client regenerated; no hand-copied API types
- [ ] **Layer boundaries** — domain has no framework imports; DTO ≠ domain ≠ JPA entity
- [ ] **Controller thin** — controllers contain routing/mapping only (§4.0.1); business logic is in `application/`
- [ ] **Service owns logic** — new/changed rules live in Service, covered by unit tests with mocked ports
- [ ] **AI isolation** — LangChain4j types stay in `ai/langchain4j`; LLM output validated before persist
- [ ] **Tests** — service unit tests added/updated; golden-file or schema tests for structured LLM output if touched
- [ ] **Frontend layers** — page thin (§4.2.6-D); logic in `features/`; hook → `lib/api/` → generated types; forms use `onValuesChange` (§4.2.6-F)
- [ ] **Frontend boundaries** — no cross-feature imports; public API via `features/*/index.ts` (§4.2.6-C)
- [ ] **Unified styling** — Tailwind classes from design tokens; Ant overrides in `globals.css`; no CSS Modules (§4.2.9)
- [ ] **API contract** — `/api/v1/` paths; GET/POST/PUT/DELETE only; error envelope `{ code, message, details }` (§6.1)
- [ ] **User scope** — data access filtered by `user_id`; no tenant fields (§4.0.5)
- [ ] **Function params** — ≤3; extras bundled in Query/Command/Props interface (§4.0.4)
- [ ] **Naming** — camelCase functions; kebab-case files/URLs; snake_case i18n (§4.0.4)
- [ ] **i18n** — no hardcoded user-facing strings; snake_case keys in locale files (§4.2.10)
- [ ] **Admin seed** — dev/docker only; `ADMIN` user after migrate; disabled in prod (§4.0.6)
- [ ] **Admin audit** — password reset / user changes logged to `audit_event` (§4.0.6)
- [ ] **No secrets** — API keys only via env/config; nothing committed
- [ ] **Stub adapters** — if vendor undecided, port + `Stub*Adapter` ships (§4.0.7)

### 12.4 Feature definition of done (per C1–C5 story)

In addition to §12.3, a **feature story** is done only when all apply:

| Criterion | Required |
|---|---|
| OpenAPI paths + error codes registered | Yes |
| Service unit tests with mocked ports | Yes |
| Controller slice test (routing only) | Yes |
| Stub adapter if external port introduced | Yes (§4.0.7) |
| Frontend: loading / error / empty states | Yes |
| i18n namespace complete for the feature | Yes |
| Golden-file or schema test for LLM output | If feature calls LLM |
| E2E or integration test on happy path | Phase 1+ features |

### 12.5 AI-specific rules

When an AI assistant generates or edits code:

1. **Read first** — load `plans/superpower/PLAN.md` (this file) and the target package before writing.
2. **Prerequisites** — if starting a new session or Phase 0, run `npm run prereq` first (§4.0.0).
3. **Follow order** — §12.2 steps 2→6; never scaffold a fat controller and "refactor later."
4. **No layer skipping** — do not put DB queries, LLM calls, or supplier HTTP in controllers "just to ship."
5. **Match conventions** — reuse existing services, mappers, DTO naming; do not invent parallel patterns.
6. **Self-check** — before finishing, run through §12.3 and §4.2.6 (frontend) mentally; fix violations before presenting the diff.
7. **Scope** — minimal diff for the requested feature; no unrelated refactors.

Violations (business logic in controller, vendor types in domain, hand-typed frontend
API shapes) must be corrected **before** the change is considered done — not in a
follow-up PR.

---

## 13. Coding rules quick reference

Full detail in §4.0.2, §4.0.4 (shared), and §4.2.6 (frontend). This section is the
at-a-glance checklist for humans and AI.

### 13.0 Shared rules (frontend & backend)

| # | Rule |
|---|---|
| X1 | **≤3 function params** — bundle into Query/Command/Props interface |
| X2 | **Interfaces control fields** — no loose maps or unbounded primitives |
| X3 | **camelCase** functions/methods |
| X4 | **kebab-case** files (frontend) & URLs/API paths |
| X5 | **snake_case** i18n files/keys & DB columns |
| X6 | **Clear, human-readable code** — name by intent; ~40 lines/method max |
| X7 | **User-based auth** — no tenant; scope all data by `user_id` (§4.0.5) |
| X8 | **Annotations OK** — Spring/Jakarta backend; interfaces + zod frontend |
| X9 | **Prerequisites** — `npm run prereq` before dev; install missing tools (§4.0.0) |
| X10 | **Docker** — full stack via `docker compose up` (§4.0.0) |
| X11 | **Gradle** — backend builds via committed wrapper (§1) |
| X12 | **Stub-first** — port + stub before live vendor adapter (§4.0.7) |

### 13.1 Backend coding rules

| # | Rule |
|---|---|
| B1 | **Java 21** — enforced in CI |
| B2 | **Controller = routing only** — ≤10 lines/endpoint; inject service, not repo/LLM |
| B3 | **Service = business logic** — orchestration, rules, `@Transactional`, call ports |
| B4 | **Domain is pure** — no Spring, JPA, LangChain4j imports |
| B5 | **LLM in `ai/` only** — LangChain4j imports restricted to `ai/langchain4j/` |
| B6 | **DTO ≠ domain ≠ JPA entity** — MapStruct mappers between all three |
| B7 | **Dependencies inward** — `api → application → domain` |
| B8 | **Money** — `BigDecimal` + currency value object; DB `numeric`; never float |
| B9 | **LLM output validated** — structured types + `Guardrails` before persist |
| B10 | **Ports in domain, adapters in infrastructure/ai** — services inject interfaces |
| B11 | **Flyway for schema** — no ddl-auto in production |
| B12 | **Typed errors** — domain exceptions → `@ControllerAdvice` → HTTP status |
| B13 | **Unit test services** — mock ports; golden-file tests for LLM output |
| B14 | **No secrets in source** — env / secret manager only |
| B15 | **Booking human-gated** — agent proposes, user confirms (§7) |
| B16 | **DSA when needed** — pure algorithms in `domain/algorithm/`; measure before optimizing (§4.0.3) |
| B17 | **LLM + DSA split** — LLM reasons; DSA ranks/validates/optimizes typed results |
| B18 | **≤3 params** — use `*Query`/`*Command`/`UserContext` DTOs (§4.0.4) |
| B19 | **camelCase methods** · **kebab-case URLs** · interfaces/records control fields |
| B20 | **Clear code** — name by intent; ~40 lines/method; comments explain why |
| B21 | **API v1** — `/api/v1/` · GET/POST/PUT/DELETE · no PATCH (§6.1) |
| B22 | **Error envelope** — `{ code, message, details }` · snake_case codes |
| B23 | **User-scoped** — filter by `user_id`; no tenant (§4.0.5) |
| B24 | **Constructor injection** — no field `@Autowired` |
| B25 | **Logging** — structured JSON; `X-Request-Id`; no PII |
| B26 | **Annotations OK** — `@Valid`, `@Transactional`, `@ControllerAdvice` |
| B27 | **Admin** — `ROLE_ADMIN` for user mgmt; seed `ADMIN`/`123456` dev only (§4.0.6) |
| B28 | **Admin audit** — all admin mutations → `audit_event` |
| B29 | **Gradle** — build via committed wrapper; Java 21 toolchain (§1, ADR 001) |
| B30 | **Stub adapters** — ship `Stub*Adapter` when vendor undecided (§4.0.7) |

### 13.2 Frontend coding rules

| # | Rule |
|---|---|
| F1 | **Node 22** — enforced via `.nvmrc` + CI |
| F2 | **Option A structure** — code in `features/<C1–C5>/`; pages in `app/` only |
| F3 | **Page = routing only** — ≤20 lines; no fetch, no Form, no React Query |
| F4 | **Feature = UI logic** — components + hooks + schemas inside feature module |
| F5 | **Data flow** — page → component → hook → `lib/api/` → generated client |
| F6 | **No raw fetch** — all API via `lib/api/` + generated types |
| F7 | **No hand-written API types** — regenerate from OpenAPI |
| F8 | **TypeScript strict** — no `any` |
| F9 | **React Query** — all server state; query keys in `lib/query/query-keys.ts` |
| F10 | **Forms** — Ant Design `Form` + **`onValuesChange`**; save in parent hook |
| F11 | **No API in forms** — forms emit values up; hooks call mutations |
| F12 | **No cross-feature imports** — share via `components/ui/` + `index.ts` exports |
| F13 | **Loading / error / empty** — every data screen handles all three |
| F14 | **Zod at API boundary** — validate responses in `lib/api/` |
| F15 | **Tailwind + Ant** — utilities for layout; Ant for widgets; unified tokens (§4.2.9) |
| F16 | **No CSS Modules / styled-components** — Tailwind only |
| F17 | **No secrets in bundle** — `NEXT_PUBLIC_*` only; AI calls via backend |
| F18 | **Booking UI** — explicit confirm button; no optimistic updates |
| F19 | **UI DSA only for display** — sort/filter/virtualize in hooks; business logic stays backend |
| F20 | **≤3 params** — use `*Request`/`*Props` interfaces (§4.0.4) |
| F21 | **camelCase functions** · **kebab-case files/URLs** · **PascalCase components** |
| F22 | **Props/form interfaces** — `XxxProps`, `XxxFormValues` on every component/form |
| F23 | **i18n required** — next-intl; snake_case keys/files; no hardcoded UI strings (§4.2.10) |
| F24 | **Clear code** — name by intent; early returns; no magic strings |
| F25 | **Error display** — map `ApiError.code` → i18n; use §6.1 envelope |
| F26 | **Auth** — attach credentials in `client.ts`; user-scoped (§4.0.5) |
| F27 | **Exports/imports** — named exports; import order; kebab-case test files |
| F28 | **Chat XSS** — sanitize LLM markdown (DOMPurify) |
| F29 | **Route files** — `loading.tsx` / `error.tsx` where applicable |
| F30 | **Admin UI** — `(admin)` routes; `features/admin/`; guard `ROLE_ADMIN` (§4.0.6) |
| F31 | **`cn()` helper** — use for all conditional Tailwind class merging |

### 13.3 API contract rules (shared)

| # | Rule |
|---|---|
| A1 | **Version** — all endpoints under `/api/v1/` |
| A2 | **Verbs** — GET / POST / PUT / DELETE only — **no PATCH** |
| A3 | **URLs** — kebab-case segments; plural nouns |
| A4 | **Errors** — `{ code, message, details }` — snake_case `code` |
| A5 | **Error display** — frontend maps `code` → i18n key |
| A6 | **Pagination** — `page`, `page_size`, `sort` on list GETs |
| A7 | **Idempotency-Key** header on booking confirm |

### 13.4 Styling rules — Tailwind + Ant Design (frontend)

| # | Rule |
|---|---|
| S1 | **Single source** — `design-tokens.ts` → Tailwind config + Ant theme |
| S2 | **Tailwind default** — layout, spacing, typography, responsive, dark mode |
| S3 | **Ant components** — Form, Table, Modal, Button — behavior + a11y |
| S4 | **Global Ant overrides** — `globals.css` `@layer components` with `@apply` |
| S5 | **Unified patterns** — use `PageShell`, shared spacing/typography conventions |
| S6 | **No CSS Modules** — Tailwind replaces project-wide |
| S7 | **No arbitrary values** — no `text-[#abc]`; extend theme in tokens instead |
| S8 | **`cn()`** — merge classes via `lib/utils/cn.ts` |
| S9 | **Ant `classNames`** — instance tweaks only after global layer |

---

## 14. Non-functional requirements (NFRs)

Initial targets — refine with production data; do not block Phase 0 on tuning.

| Area | Target | Notes |
|---|---|---|
| **API latency** (excl. AI) | p95 < 500 ms | Measured at `/api/v1/` CRUD endpoints |
| **Research agent** (C2) | p95 < 90 s | Bounded tool loop; show progress in UI |
| **Itinerary generation** (C3) | p95 < 60 s | Structured output + validation |
| **Booking confirm** (C4) | p95 < 10 s | Excludes payment provider redirect |
| **Availability** (v1) | Best effort local/docker | No SLA until production deploy |
| **Concurrent users** (v1) | ~50 simultaneous | Single-region; scale story post-v1 |
| **Data retention** | User data until account delete | `ai_call_log` retained 90 days |
| **Uptime monitoring** | `/health` + `/ready` polled | Compose healthchecks + CI smoke |
| **Rate limits** | AI endpoints: 20 req/min/user | Booking confirm: 5 req/min/user |
| **Security** | No PII in logs; secrets in env only | §4.0.2-J2, §4.0.5 |

---

## 15. CI/CD pipeline (LOCKED)

Pipeline mirrors §12.2 workflow. Runs on every PR to `master`.

### 15.1 Stages

```
lint → build → test → contract → docker → smoke
```

| Stage | Backend | Frontend | Gate |
|---|---|---|---|
| **lint** | Checkstyle/Spotless (Gradle) | ESLint + TypeScript `tsc --noEmit` | Fail on error |
| **build** | `./gradlew build -x test` | `npm run build` | Compile success |
| **test** | `./gradlew test` | `npm run test` (Vitest) | All pass |
| **contract** | OpenAPI validate | `npm run codegen` + git diff check | No drift |
| **migrate** | Flyway validate (Testcontainers) | — | Migrations valid |
| **docker** | Build `docker/backend/Dockerfile` | Build `docker/frontend/Dockerfile` | Image builds |
| **smoke** | Hit `/api/v1/ready` in compose | — | Healthy stack |

### 15.2 Runtime version gates

- Node **22.x** — `node --version` in CI
- Java **21** — Gradle toolchain enforcement
- Docker Compose **v2+** — for integration job

### 15.3 Prompt & AI gates (Phase 1+)

- Any change under `ai/prompt/` triggers **eval harness** job
- Golden-file schema tests must pass before merge

### 15.4 Branch policy

- `master` — protected; PR required; CI green
- Branch naming: `feature/{ticket}-short-desc`, `fix/{ticket}-short-desc`, `cursor/{desc}-{id}`

---

## 16. Architecture decision records (ADRs)

Significant decisions are recorded in `docs/adr/` and referenced from §11.

| ADR | Title | Status |
|---|---|---|
| [001](../../docs/adr/001-gradle.md) | Gradle as backend build tool | Accepted |
| [002](../../docs/adr/002-jwt-auth.md) | JWT in httpOnly cookie for v1 auth | Accepted |

**When to write an ADR:** changing a locked decision in §1, swapping LLM framework,
adding a new runtime service, or altering API versioning strategy.
