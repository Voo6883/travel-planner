# Task 20 — Conversation Persistence and SSE

## Objective

Build the durable chat transport and persistence layer shared by planner-level and per-trip conversations, without adding business tools yet.

## Dependencies

- Tasks 06, 07, 11, 14, and 15 complete.

## Required reading

- `plans/superpower/PLAN.md` §3.2 and chat architecture sections
- `plans/USE-CASES.md` UC-C5-08 and UC-C5-09
- `docs/ARCHITECTURE-DIAGRAMS.md` chat flow

## Scope

### Data and backend

- Migrations/domain/persistence for `planner_session`, `conversation`, and `message`.
- Message roles/types sufficient for user, assistant, system metadata, tool calls/results, and lifecycle events without storing hidden chain-of-thought.
- User ownership, conversation-trip linkage, ordering, timestamps, pagination/history loading, and archived/read-only behavior.
- SSE endpoints for planner and trip messages under the accepted `/api/v1/` paths.
- Defined event envelope for token deltas, message start/end, tool lifecycle, `trip_created`, typed errors, completion, and heartbeat.
- Cancellation and disconnect handling; partial assistant messages must be explicitly marked or safely discarded.
- Idempotency/client message ID to avoid duplicate sends on retry.
- Conversation context assembly interface, but no C1/C2/C3 tools yet.

### Frontend

- Reusable chat transport hook, event parser, reconnect/error behavior, optimistic user-message handling with deduplication, history loading, and basic chat panel components.
- Rendering plain safe text only; rich Markdown security is completed in Task 36.

### Tests

- Persistence ordering/ownership.
- SSE event-contract tests.
- Disconnect/cancel/duplicate-message cases.
- Frontend event parser and state reducer tests.

## Do not

- Do not expose internal reasoning or raw provider events.
- Do not add business tools or create trips.
- Do not hold database transactions during streaming/model calls.
- Do not cache chat/API responses in the PWA.

## Definition of Done

- Durable chat messages reload in order.
- SSE contract is typed and stable for later orchestrators.
- Disconnect/retry cannot duplicate committed user messages.
- No hidden chain-of-thought is stored or returned.

## Handoff

Document event schemas, persistence semantics, context interface, and frontend hook APIs.

## Suggested branch and commit

- Branch: `agent/task-20-conversation-sse`
- Commit: `feat: add durable conversation and SSE transport`
