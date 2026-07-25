# Task 07 — Database and Domain Foundation

## Objective

Establish Flyway, persistence testing, core value objects, user/trip foundations, and transaction patterns required by all business features.

## Dependencies

- Tasks 02, 04, and 06 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0.2, §8, transaction and deadlock rules
- `plans/BACKLOG.md` S1-2, S1-3, S1-7, S1-9
- `plans/USE-CASES.md` trip status table

## Scope

- PostgreSQL and pgvector extension migration baseline.
- Flyway configuration for local, Docker, test, and production profiles.
- Initial user, user identity, trip, and audit columns required by dependent tasks; avoid tables belonging exclusively to later features.
- Pure domain types including `Money`, `DateRange`, `UserContext`, and `TripStatus`.
- Domain invariants with no Spring/JPA/LangChain4j imports.
- Persistence adapters and explicit mapping between JPA and domain objects.
- MapStruct configuration.
- Optimistic-locking pattern for mutable aggregates.
- Transaction templates, rollback behavior, consistent lock-order documentation, and retry policy for lock failures.
- Testcontainers integration and migration validation tests.

## Do not

- Do not create the complete knowledge, chat, itinerary, or booking schemas early.
- Do not use `double` for money.
- Do not call external services or LLMs inside transactions.
- Do not expose JPA entities outside infrastructure.

## Validation

```bash
cd apps/backend
./gradlew clean test
./gradlew flywayValidate
```

Run integration tests proving rollback on forced failure and optimistic-lock conflict behavior.

## Definition of Done

- Migrations apply from an empty database.
- Domain compiles without framework dependencies.
- Persistence mappings and transaction behavior are tested.
- Dependent tasks can create user-scoped aggregates safely.

## Handoff

Record migration numbering, table lock order, ID strategy, timezone rules, and mapping conventions.

## Suggested branch and commit

- Branch: `agent/task-07-domain-db-foundation`
- Commit: `feat: add database and domain foundation`
