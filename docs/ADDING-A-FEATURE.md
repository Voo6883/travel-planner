# Adding a New Feature

> Step-by-step guide for humans and AI agents. Implements PLAN §4.0.8.
> Follow [`AI-AGENT-WORKFLOW.md`](AI-AGENT-WORKFLOW.md) pipeline in parallel.

## When to use this guide

- New product capability **C6+** (packing list, reviews, …)
- New sub-capability under existing C1–C5 (e.g. new research filter)
- New API resource or screen

**Do not** use for bug fixes or refactors — minimal diff only.

---

## Naming convention

| Item | Pattern | Example |
|---|---|---|
| Feature ID | `C{n}` or descriptive slug | `C6` → `packing` |
| Backend package | `application/<feature>/` | `application/packing/` |
| Service | `<Feature>Service` | `PackingListService` |
| Controller | `<Feature>Controller` | `PackingListController` |
| Frontend folder | `features/<feature>/` | `features/packing/` |
| i18n file | `locales/en/<feature>.json` | `packing.json` |
| GraphQL types | `<feature>` in `graphql/<feature>.graphqls` | `packing.graphqls` |
| OpenAPI tag | `<feature>` (REST-only endpoints only) | `packing` |
| Flyway | `V{n}__create_<feature>_table.sql` | `V10__create_packing_list_table.sql` |

---

## Step 1 — Plan & contract

1. Add story to [`plans/BACKLOG.md`](../plans/BACKLOG.md) (or confirm ticket).
2. Add GraphQL types, queries, mutations in `resources/graphql/<feature>.graphqls`.
3. Add REST paths under `/api/v1/` **only** if transport requires (SSE, 202, OAuth).
4. Register new error codes (`snake_case`) — shared across GraphQL and REST.
5. Run `npm run codegen`.

**Gate:** Generated TS clients include new types; CI contract job would pass.

---

## Step 2 — Domain (if new concepts)

```
domain/model/PackingList.java
domain/port/PackingListPort.java          # if persistence needed
domain/exception/PackingListNotFoundException.java
```

- Zero framework imports.
- Value objects for any new domain concepts.

---

## Step 3 — Application service

```
application/packing/PackingListService.java
application/packing/CreatePackingListCommand.java
application/packing/PackingListServiceTest.java    # mock ports
```

- `@Transactional(rollbackFor = Exception.class)` on writes.
- No LLM/HTTP inside transaction.
- Public methods only for use-cases; helpers `private`.

---

## Step 4 — Infrastructure

```
infrastructure/persistence/PackingListEntity.java
infrastructure/persistence/PackingListRepository.java
db/migration/V{n}__create_packing_list_table.sql
```

- Forward-only migration — never edit old `V*` files.
- FK to `trip(id)` + `user_id` scope on queries.
- `@Version` if concurrent edits expected.

---

## Step 5 — API layer

```
api/graphql/PackingListResolver.java         # ≤10 lines per query/mutation
api/graphql/input/CreatePackingListInput.java
api/graphql/PackingListGraphQlMapper.java
api/controller/PackingListController.java    # REST-only if needed (e.g. export SSE)
api/dto/packing/                           # REST DTOs if applicable
```

- Resolver/controller injects `PackingListService` only.
- Map domain → GraphQL types via dedicated mapper (not JPA entities).

---

## Step 6 — AI layer (only if feature uses LLM)

```
ai/agent/PackingListAgent.java
ai/tool/PackingListTool.java                 # implements AgentTool
ai/prompt/packing-list-v1.txt
config/AgentToolConfig.java                  # registry.register("packing", ...)
```

- Register tool in `ToolRegistry` — do not edit other agents' code.
- Golden-file test for structured output.

---

## Step 7 — Frontend

```
features/packing/
  components/packing-panel.tsx
  hooks/use-packing-list.ts
  schemas/packing.schema.ts
  types.ts
  index.ts
lib/graphql/packing-queries.ts
lib/rest/packing-rest.ts                     # only if REST endpoint needed
lib/query/query-keys.ts                      # add packing: { ... }
locales/en/packing.json
app/(planner)/trips/[tripId]/packing/page.tsx   # thin page
```

1. Copy structure from `features/_template/` (created in Phase 0b).
2. Add step to `trip-stepper.tsx` + i18n `common.trip_step_packing`.
3. Loading / error / empty states required.

---

## Step 8 — Feature flag (post-v1 or risky features)

```yaml
# config/features.yml
features:
  packing-list: true
```

```java
@ConditionalOnProperty(name = "features.packing-list", havingValue = "true")
```

---

## Step 9 — Verify

```bash
./gradlew test jacocoTestReport archunitTest
cd apps/frontend && npm run lint && npm run test && npm run build
npm run codegen && git diff --exit-code apps/frontend/src/generated/
```

### Checklist

- [ ] §12.3 PR checklist (PLAN.md)
- [ ] §12.4 feature DoD
- [ ] ArchUnit layer rules pass
- [ ] JaCoCo ≥70% on new `application/<feature>/` code
- [ ] No edits to unrelated feature services
- [ ] Feature flag if post-v1

---

## Anti-patterns (do not do)

| ❌ | ✅ |
|---|---|
| Add `generatePackingList()` to `TripService` | New `PackingListService` |
| Import LangChain4j in `application/` | `ai/langchain4j/` adapter |
| Edit `V3__create_trip.sql` | New `V{n}__...` migration |
| Hand-write TS types | GraphQL + OpenAPI → codegen |
| Import `features/research` from `features/packing` | Shared UI in `components/ui/` |
| Skip schema/OpenAPI before coding | Contract first (ADR 005) |

---

## Related docs

| Doc | Purpose |
|---|---|
| [`plans/superpower/PLAN.md`](../plans/superpower/PLAN.md) §4.0.8–4.0.9 | Extensibility + industry standards |
| [`AI-AGENT-WORKFLOW.md`](AI-AGENT-WORKFLOW.md) | Full generation pipeline |
| [`AGENTS.md`](../AGENTS.md) | Agent entry point |
