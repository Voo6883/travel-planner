# Task 14 — AI Provider Platform

## Objective

Implement the provider-neutral AI runtime used by C1, C2, C3, and C5 without implementing feature agents.

## Dependencies

- Tasks 07 and 09 complete.
- Task 06 contract/error conventions available.

## Required reading

- `plans/superpower/PLAN.md` §5 and AI sections of §4.1 — **§5.1's `Flux<String> stream(...)` is superseded; see ADR 007**
- **[`docs/adr/007-chat-streaming-transport.md`](../docs/adr/007-chat-streaming-transport.md) — Accepted, and higher authority than this brief and than PLAN §5.1**
- [`docs/adr/010-tkb-data-sourcing-embeddings.md`](../docs/adr/010-tkb-data-sourcing-embeddings.md) §5 — pins the embedding model and dimension
- `plans/BACKLOG.md` S2-3, S2-4, S2-5
- `docs/AI-AGENT-WORKFLOW.md` AI addendum

## Scope

- Provider-neutral `LlmClient`, `EmbeddingClient`, prompt/options, structured completion, and tool-call abstractions.

### Streaming event model (ADR 007 — supersedes PLAN §5.1)

- `LlmPort` returns **`Flux<LlmEvent>`**, not `Flux<String>`. A token stream structurally cannot carry tool-use deltas, stop reasons, usage, or a mid-stream error, which makes the locked `trip_created` behaviour unimplementable on top of it.
- `LlmEvent` is a **sealed interface**: `TextDelta` · `ToolUseStart` · `ToolInputDelta` · `ToolUseEnd` · `ToolResult` · `DomainEvent` · `Usage` · `Done` · `StreamError`.
- Anthropic and OpenAI adapters normalise their native stream events into this union inside `ai/langchain4j/`. Both providers emit events, not strings — normalising to `String` discards exactly what the product needs.
- `Usage(inputTokens, outputTokens, cachedTokens)` is what makes `ai_call_log` implementable at the port boundary; without it token accounting has nowhere to come from.
- `StreamError` carries the §6.1 envelope, because once a stream has returned `200` the error can no longer be delivered by HTTP status.
- The sealed type is the backend half of a contract whose frontend half is hand-authored (`lib/api/chat-stream.ts`, the single documented codegen exception). Task 20 owns the wire format; this task owns the union.
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
- Do not mix embedding dimensions/providers in one index — pin `text-embedding-3-small` / 1536 per ADR 010 §5.
- **Do not expose `Flux<String>` from the port**, and do not JSON-encode events into text frames — that pushes protocol parsing into every consumer and loses type safety at the boundary (ADR 007, alternatives considered).
- Do not implement the SSE transport, heartbeat, or resume here; [Task 20](20-conversation-sse.md) owns them.
- Do not emit `DomainEvent` by parsing assistant prose — it is emitted by the orchestrator after the tool commits.

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
