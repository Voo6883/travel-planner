# Task 22 — Trip Chat Intake Tools

## Objective

Connect per-trip chat to deterministic C1 services using status-gated tools and maintain bidirectional synchronization with the TripBrief form.

## Dependencies

- Tasks 18–21 complete.

## Required reading

- `plans/superpower/PLAN.md` trip-level tools and LLM decision policy
- `plans/USE-CASES.md` UC-C5-01, C5-02, C5-08, C5-09
- `plans/BACKLOG.md` S3-7 trip portion

## Scope

- `TripChatOrchestrator` foundation with current `trip.status`, brief, pending clarification, and conversation context.
- Tools:
  - `update_trip_brief` allowed only in `DRAFT`/`CLARIFICATION_NEEDED`.
  - `answer_clarification` allowed only in `CLARIFICATION_NEEDED`.
- Tool inputs map to application commands; domain services revalidate all data.
- Status-gate rejection returns typed tool/error events and does not mutate state.
- Chat updates invalidate/refetch the same brief query used by the form; form updates appear in subsequent chat context.
- Assistant response summarizes validated changes without claiming rejected fields were saved.
- Concurrency handling for simultaneous form/chat edits using optimistic locking and visible conflict recovery.
- E2E flow from planner chat through `BRIEF_COMPLETE`, reload, and continued conversation.
- Evaluation fixtures for premature action, missing fields, malicious tool arguments, repeated clarification, and archived trips.

## Do not

- Do not add `start_research` or other later tools.
- Do not let model prose directly update the database.
- Do not bypass TripBrief validation or optimistic locking.

## Definition of Done

- Chat and form share one authoritative TripBrief.
- Status gates are enforced server-side.
- Conflicts and rejected tool calls are visible and recoverable.
- C1 chat E2E reaches `BRIEF_COMPLETE` safely.

## Handoff

Document trip context shape, tool registry/gate extension procedure, query invalidation behavior, and approved states for Task 23/27.

## Suggested branch and commit

- Branch: `agent/task-22-trip-chat-intake`
- Commit: `feat: add status-gated intake chat tools`
