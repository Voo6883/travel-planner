# Task 18 — Trip and TripBrief Core

## Objective

Implement the deterministic Trip lifecycle, typed TripBrief, clarification model, APIs, and structured frontend editor before adding LLM extraction.

## Dependencies

- Tasks 06, 07, 11, 15, and 17 complete.

## Required reading

- `plans/USE-CASES.md` trip lifecycle and C1
- `plans/superpower/PLAN.md` §3.1, §4.1.3, frontend intake rules
- **[`docs/adr/008-optimistic-concurrency.md`](../docs/adr/008-optimistic-concurrency.md) — Accepted. The auto-save behaviour in this brief is the exact race it exists to prevent.**
- `plans/BACKLOG.md` S1-8 and S3-1 through S3-6, excluding LLM extraction owned by Task 19

## Scope

### Backend

- User-scoped Trip aggregate/repository/service and list/get/create/update/archive/delete behavior required by the plan.
- `TripBrief` value/aggregate structure covering destination preferences, dates/flexibility, departure, budget/currency, party, interests, pace, and other locked fields.
- Domain validation for money, dates, party size, unsupported transitions, and archived read-only behavior.
- `ClarificationNeeded`, typed questions/options, pending answers, and revalidation.
- Status transitions among `DRAFT`, `CLARIFICATION_NEEDED`, and `BRIEF_COMPLETE` only.
- OpenAPI endpoints for trip and brief editing/clarification.

### Concurrency (ADR 008)

- `version integer not null default 0` with JPA `@Version` on `trip` and `trip_brief`.
- Every read preceding a write returns `version`; every write carries `expected_version` in the body; mismatch returns `409 version_conflict` with `details.current_version`.
- Every mutation returns the **new** full resource with its incremented `version`.
- `POST .../brief/actions/answer-clarification` as a typed action rather than a full-body `PUT` — the no-`PATCH` rule stands, but re-sending a whole aggregate to answer one question does not scale.

> **This is not premature.** The brief already specifies a debounced auto-save, and from Task 19
> the agent writes the same aggregate through `update_trip_brief`. Both issue whole-object writes;
> whichever lands second silently discards the other, including server-computed fields. The
> product's "chat and form are synced mirrors" claim has no mechanism without this.

### Frontend

- Trip list and trip shell placeholders required for C1.
- `features/intake/` form with generated types, Zod shape checks, Ant Design Form, auto-save/debounce, save status, and clarification UI.
- Loading/error/empty/conflict/archived states.
- **No optimistic updates on any entity the agent can also mutate** (ADR 008 §5) — server truth arrives concurrently and an optimistic write racing an invalidation produces flicker. Optimistic UI is permitted only for pure-UI state.
- Conflict UX: on `409`, re-fetch, re-apply the user's uncommitted field edits onto fresh state, and show a non-blocking "updated by assistant" notice. Never overwrite a field the user currently has focused.
- English/Malay translations.

### Tests

- Domain transition tests.
- Ownership/isolation tests.
- API contract tests.
- Frontend form and auto-save tests.
- Deterministic C1 E2E using forms only.

## Do not

- Do not call an LLM.
- Do not start research.
- Do not implement chat or itinerary.
- Do not silently guess missing fields.

## Definition of Done

- A user can create and complete a valid brief without AI.
- Invalid/incomplete briefs produce typed clarification.
- C2 remains blocked until `BRIEF_COMPLETE`.
- Form and server state stay consistent.

## Handoff

Provide final TripBrief/clarification schemas, transition table, API routes, and service entry points used by Tasks 19, 21, and 22.

## Suggested branch and commit

- Branch: `agent/task-18-trip-brief-core`
- Commit: `feat: implement deterministic trip intake core`
