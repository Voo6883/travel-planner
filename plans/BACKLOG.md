# Travel Planner — Delivery Backlog

Sprint-ready epics and stories derived from [`plans/superpower/PLAN.md`](superpower/PLAN.md) §10.

**Assumption:** 2-week sprints. Story IDs map to Jira/Linear tickets (`S0-1`, `S3-2`, etc.).

---

## Epic map

| Epic | ID | Phase | Outcome |
|---|---|---|---|
| DevEx & Runtime | E-00 | 0a | `docker compose up` works |
| API Platform | E-01 | 0b | `/api/v1/`, error envelope, health |
| Identity & Admin | E-02 | 0b | JWT auth, admin CRUD |
| AI Platform | E-03 | 0b | `LlmClient` router + logging |
| Frontend Platform | E-04 | 0b | App shell, design system, codegen |
| C1 Intake | E-10 | 1 | TripBrief E2E |
| C2 Research | E-11 | 1 | Ranked recommendations E2E |
| C3 Itinerary | E-12 | 1 | Day-by-day plan E2E |
| C4 Booking | E-20 | 2 | Quote → confirm flow |
| C5 Chat | E-21 | 2 | SSE refinement |

---

## Sprint 0 — DevEx (E-00)

**Goal:** clone → `npm run prereq` → `docker compose up` → healthy stack.

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S0-1 | Prereq scripts | `check-prerequisites.sh`, `.ps1`, root `package.json`, `.nvmrc` | Non-zero exit with install hints; `npm run prereq` works |
| S0-2 | Docker stack | `docker-compose.yml`, `docker-compose.dev.yml`, `.env.example`, Dockerfiles | postgres + backend + frontend start; healthchecks pass |
| S0-3 | Wait script | `wait-for-services.sh` polls `/api/v1/ready` | Blocks until postgres + backend healthy |
| S0-4 | CI skeleton | GitHub Actions: node 22, java 21, docker build | PR fails on wrong runtime |
| S0-5 | PR template | Embed §12.3 checklist + link to `docs/AI-AGENT-WORKFLOW.md` | Every PR has checklist |

---

## Sprint 1 — Backend platform (E-01, E-02 partial)

**Goal:** `GET /api/v1/health` + JWT login + trip CRUD stub with error envelope.

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S1-1 | Gradle + Spring scaffold | `build.gradle.kts`, package layout §4, profiles | Compiles Java 21 |
| S1-2 | Flyway + DB | `V1__create_user_table.sql`, pgvector | Migrations on startup |
| S1-3 | Domain VOs | `Money`, `DateRange`, `UserContext` | Unit tests; no framework imports |
| S1-4 | Error envelope | `DomainException`, `@ControllerAdvice` | Contract test for 404 shape |
| S1-5 | OpenAPI bootstrap | Health, auth, trip paths in `api/openapi/` | Spec validates |
| S1-6 | JWT auth | login/logout/me; httpOnly cookie | Unauthorized → `forbidden` |
| S1-7 | MapStruct | Mapper config + example flow | Compile-time mapping works |
| S1-8 | Trip scaffold | `GET/POST /api/v1/trips` | Thin controller; service unit test |
| S1-9 | Checkstyle + transactions | `LineLength` 120; `@Transactional(rollbackFor)` template; Spring Retry | CI lint + integration rollback test |

---

## Sprint 2 — AI + frontend platform (E-02, E-03, E-04)

**Goal:** Generated client calls authenticated API; admin works; LLM router smoke-tested.

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S2-1 | Admin seed | `V2__seed_dev_admin_user.sql`, profile-gated | `ADMIN`/`123456` in docker only |
| S2-2 | Admin APIs | `AdminUserService`, audit logging | `@PreAuthorize ADMIN` |
| S2-3 | LlmClient ports | Interfaces + `LlmClientRouter` | Unit test with mock |
| S2-4 | LangChain4j adapters | Anthropic + OpenAI in `ai/langchain4j/` | Config switch works |
| S2-5 | AI observability | `ai_call_log`, `X-Request-Id` MDC | Token count persisted |
| S2-6 | Next.js scaffold | `(planner)/`, `(admin)/`, stepper placeholder | Builds on Node 22 |
| S2-7 | Design system | tokens, Tailwind, Ant theme, `PageShell` | 2 sample pages consistent |
| S2-8 | i18n | next-intl, `en/` + `ms/` | No hardcoded strings on scaffold |
| S2-9 | Codegen pipeline | `npm run codegen`, CI drift check | Fails when spec stale |
| S2-10 | API client layer | `lib/api/client.ts`, zod, React Query | Cookie auth + request ID |
| S2-11 | Admin UI | `features/admin/`, reset password | Non-admin redirected |
| S2-12 | Lint config | ESLint `max-len` 120, Prettier printWidth 100 | CI enforces line length |

**Phase 0 gate:** all S0 + S1 + S2 DoD met; §10 Phase 0 exit criteria checked.

---

## Sprint 3 — C1 Intake (E-10)

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S3-1 | Contract | `TripBrief` schemas, `PUT .../brief` | Codegen regenerated |
| S3-2 | Domain | `TripBrief` aggregate + invariants | Domain unit tests |
| S3-3 | Extraction service | `IntakeService` + structured LLM + Guardrails | Golden-file test |
| S3-4 | C1 API | Thin controller, mappers | Service tests |
| S3-5 | C1 frontend | `features/intake/`, form `onValuesChange`, debounced save | All UI states |
| S3-6 | C1 pages | `trips/new`, `trips/[id]/brief` | E2E: create + save brief |

---

## Sprint 4 — C2 Research (E-11)

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S4-1 | Stub ports | `StubSearchAdapter`, `StubHistoricalAdapter`, etc. | Local dev without API keys |
| S4-2 | Agent loop | `TravelResearchAgent`, tool budget | Bounded loop |
| S4-3 | DSA ranking | `DestinationRanker` in `domain/algorithm/` | Table-driven tests |
| S4-4 | Research API | `POST .../research/run`, `GET .../ranked-recommendations` | User-scoped |
| S4-5 | C2 frontend | `features/research/` | Loading/error/empty |
| S4-6 | Eval harness v0 | CI on `ai/prompt/` changes | Schema regression fails CI |

---

## Sprint 5 — C3 Itinerary (E-12)

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S5-1 | Data model | `itinerary_day`, `itinerary_item` migrations | Flyway + domain |
| S5-2 | Itinerary service | `ItineraryDayPlanner`, LLM structured output | Overlap checks |
| S5-3 | C3 API + frontend | `features/itinerary/` | Day-by-day from server |
| S5-4 | E2E | intake → research → itinerary | Playwright happy path |

---

## Sprint 6 — C4 Booking search/quote (E-20)

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S6-1 | State machine | `DRAFT → QUOTED → HELD → CONFIRMED` (§7) | Persisted transitions |
| S6-2 | Stub suppliers | `StubFlightSearchAdapter`, `StubHotelSearchAdapter` | Fixture quotes |
| S6-3 | Booking API | Search + quote endpoints | User-scoped |
| S6-4 | C4 browse UI | `features/booking/` quote list | No optimistic confirm |

---

## Sprint 7 — C4 Booking confirm (E-20)

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S7-1 | Idempotency | `Idempotency-Key` header dedup | Integration test |
| S7-2 | Payment port | Stub + live adapter slot | Price re-validation at confirm |
| S7-3 | Confirm API + UI | Explicit confirm button; audit trail | No LLM auto-book |
| S7-4 | Booking errors | Typed reason codes → i18n | §6.1 envelope |

---

## Sprint 8 — C5 Chat (E-21)

| ID | Story | Tasks | DoD |
|---|---|---|---|
| S8-1 | SSE endpoint | Stream from backend | `use-chat-stream.ts` |
| S8-2 | Persistence | `conversation`, `message` tables | Multi-turn memory |
| S8-3 | Itinerary patch | Deterministic diff tool | Not free-text replace |
| S8-4 | XSS guard | DOMPurify on markdown | Sanitized render |
| S8-5 | Redis cache | Compose profile `cache` | Semantic cache optional |

---

## Definition of Ready (story enters sprint)

- [ ] OpenAPI paths drafted or spike complete
- [ ] Port interfaces named
- [ ] Error codes listed
- [ ] i18n namespace identified
- [ ] Stub vs live adapter decision documented (stub default)

## Definition of Done (story completes)

See PLAN §12.3 (PR checklist) + §12.4 (feature DoD).

---

## Risk register

| Risk | Mitigation |
|---|---|
| Phase 0 too large | Split 0a (Sprint 0) / 0b (Sprints 1–2) — done in plan |
| Vendor API delays | Stub adapters §4.0.7 — S4-1, S6-2 |
| LLM output drift | Golden files + eval harness — S4-6 |
| Admin seed in prod | Flyway profile gating — S2-1 |
