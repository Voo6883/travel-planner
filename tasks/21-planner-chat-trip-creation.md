# Task 21 — Planner Chat and Trip Creation

## Objective

Implement the pre-trip chat experience and safe handoff from a planner session to a new Trip.

## Dependencies

- Tasks 18, 19, and 20 complete.

## Required reading

- `plans/superpower/PLAN.md` §2 and §3.2 planner-level policy
- `plans/USE-CASES.md` UC-T01 and UC-C5-00/01
- `plans/BACKLOG.md` S3-7 planner portion

## Scope

- `PlannerChatOrchestrator` using the Task 14 AI abstraction and Task 20 transport.
- Planner-level tool registry exposing only `create_trip`.
- Decision policy: vague opener asks one or two high-impact questions; enough information may create a trip and apply a validated brief.
- `create_trip` command with user ownership, generated safe name, idempotency, and short transaction.
- Atomically link/migrate planner-session messages to the new trip conversation without loss or duplication.
- Emit typed `trip_created` SSE event and navigate frontend to `/trips/{tripId}` while preserving the conversation.
- Suggested prompts on planner home and complete loading/error/retry states.
- Guardrails preventing tool execution from arbitrary model text.
- Tests for vague opener, partial/complete input, duplicate tool call, failed trip creation, session handoff, and cross-user isolation.

## Do not

- Do not start research from planner-level chat.
- Do not expose trip-level tools before handoff.
- Do not create a trip from a vague greeting.
- Do not let the model choose user IDs or persistence identifiers.

## Definition of Done

- User can begin in planner chat and reach a durable Trip conversation when intent is sufficient.
- Vague input remains pre-trip.
- Handoff is transactional, idempotent, and E2E-tested.
- Frontend navigation follows the typed event rather than parsing assistant prose.

## Handoff

Document planner prompt/tool schema, trip naming rules, handoff transaction, and context passed to Task 22.

## Suggested branch and commit

- Branch: `agent/task-21-planner-chat-trip`
- Commit: `feat: create trips through planner chat`
