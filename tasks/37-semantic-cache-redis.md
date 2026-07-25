# Task 37 — Semantic Cache and Redis

## Objective

Add the optional Redis semantic-cache profile after core behavior is correct, with safe invalidation and no effect on money-touching or private mutation paths.

## Dependencies

- Tasks 14, 17, 25, 30, 35, and 36 complete.

## Required reading

- `plans/superpower/PLAN.md` §5.3 and cache-related runtime rules
- `plans/BACKLOG.md` S8-4
- PWA network-only and privacy constraints

## Scope

- Redis 7 optional Compose profile `cache`; default stack remains runnable without it.
- Provider-neutral cache port and Redis adapter isolated from feature services.
- Cache only eligible read/generation results with explicit policy: normalized request, locale, user-privacy scope where necessary, model/provider/version, prompt version, knowledge version/freshness, and embedding-index version.
- TTL and invalidation on knowledge refresh, prompt/model change, trip/brief/itinerary mutation, and provider configuration change.
- Fail-open behavior when Redis is unavailable; correctness must not depend on cache.
- Observability for hit/miss/bypass/error and latency/cost savings without logging sensitive keys.
- Prevent cross-user leakage and semantic false matches with thresholds and exact policy checks.
- Explicit bypass for auth, chat streaming state, booking/payment confirmation, idempotency, and mutable private API responses.
- Contract/integration/load tests with Redis profile.

## Do not

- Do not cache booking confirmation/payment results.
- Do not make Redis required for normal local startup.
- Do not reuse cached factual content after source/prompt/model invalidation.
- Do not store raw PII in keys.

## Definition of Done

- Optional cache improves eligible paths without changing outputs/authorization.
- Redis outage is transparent except performance.
- Invalidation/versioning prevents known stale-data reuse.
- Cross-user and money-path isolation is tested.

## Handoff

Document eligibility matrix, key/version design, TTL/invalidation, Compose usage, metrics, and disable procedure.

## Suggested branch and commit

- Branch: `agent/task-37-semantic-cache`
- Commit: `feat: add optional Redis semantic cache`
