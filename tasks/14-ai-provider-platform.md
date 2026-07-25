# Task 14 — AI Provider Platform

## Objective

Implement the provider-neutral AI runtime used by C1, C2, C3, and C5 without implementing feature agents.

## Dependencies

- Tasks 07 and 09 complete.
- Task 06 contract/error conventions available.

## Required reading

- `plans/superpower/PLAN.md` §5 and AI sections of §4.1
- `plans/BACKLOG.md` S2-3, S2-4, S2-5
- `docs/AI-AGENT-WORKFLOW.md` AI addendum

## Scope

- Provider-neutral `LlmClient`, `EmbeddingClient`, prompt/options, structured completion, tool-call, and streaming abstractions.
- `LlmClientRouter` for default and optional feature-specific routing.
- Anthropic and OpenAI adapters isolated under `ai/langchain4j/`.
- Embedding provider pinned per index; configuration must prevent mixing incompatible vectors.
- Timeouts, bounded retries, provider error normalization, cancellation, and circuit-breaker extension points.
- Token/latency/provider/model/cost metadata captured in `ai_call_log` without storing full production prompts or PII.
- Stub/deterministic AI adapters for tests and local operation without keys.
- Prompt-template and structured-output runner foundations.
- Configuration validation ensuring selected providers have required credentials.

## Do not

- Do not create TripBrief, research, itinerary, or chat prompts.
- Do not call providers from controllers.
- Do not require live keys in CI.
- Do not mix embedding dimensions/providers in one index.

## Validation

- Unit tests for routing, option mapping, structured-output errors, streaming cancellation, timeout, and provider fallback behavior.
- Adapter contract tests using fakes/mocks.
- Optional manually approved live smoke test with no user data.

## Definition of Done

- Features can depend only on project-owned AI interfaces.
- Both providers and deterministic stubs satisfy shared contracts.
- Observability is persisted safely.
- Embedding-provider compatibility is enforced.

## Handoff

Document configuration, provider capability differences, retry policy, logging fields, and how feature agents register prompts/tools.

## Suggested branch and commit

- Branch: `agent/task-14-ai-platform`
- Commit: `feat: establish provider-neutral AI platform`
