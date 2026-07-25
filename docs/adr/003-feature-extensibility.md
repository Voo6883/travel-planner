# ADR 003: Vertical-slice feature extensibility

## Status

Accepted

## Context

The product roadmap includes v1 features C1–C5 and post-v1 capabilities (packing, reviews,
group planning, etc.). The codebase must allow **adding new features without modifying
existing feature code**, matching industry Open/Closed and hexagonal architecture practice.

## Decision

1. **Vertical slices** — each feature owns `application/<feature>/`, `api/controller/`,
   `features/<feature>/`, and optional `ai/agent/` packages.
2. **No god services** — do not add unrelated methods to `TripService` or `ResearchService`.
3. **Registries** — agent tools, error codes, query keys, and feature flags use registration
   patterns instead of central `switch` statements.
4. **Forward-only Flyway** — schema changes only via new `V{n}__*.sql` files.
5. **Feature flags** — post-v1 features ship behind `features.<name>` config until stable.
6. **ArchUnit** — CI enforces layer boundaries so new code cannot break hexagonal rules.

## Consequences

- Adding C6+ follows [`docs/ADDING-A-FEATURE.md`](../ADDING-A-FEATURE.md) checklist.
- Trip stepper and OpenAPI tags are the only shared touchpoints for navigation/API grouping.
- Slightly more packages than a monolithic service — better isolation and parallel development.

## Alternatives considered

- **Single `TripFacade` service** — rejected; becomes unmaintainable as features grow.
- **Microservices per feature** — rejected for v1; monorepo vertical slices sufficient.
