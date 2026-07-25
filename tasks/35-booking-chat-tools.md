# Task 35 — Booking Chat Tools

## Objective

Allow trip chat to search and explain booking quotes while preserving the explicit C4 UI confirmation boundary.

## Dependencies

- Tasks 27, 31, 32, and 33 complete.

## Required reading

- `plans/superpower/PLAN.md` booking chat/safety policy
- `plans/USE-CASES.md` UC-C5-10
- `plans/BACKLOG.md` S8-2

## Scope

- `search_booking_quotes` tool allowed only in `ITINERARY_READY` or later accepted states.
- Tool maps structured user constraints to Task 32 search services; supplier/quote facts come only from persisted/provider results.
- Assistant can summarize/compare quotes, expiry, price, fees, and terms with references to quote IDs.
- Typed SSE events/links navigate the user to the structured booking screen.
- Chat may select a quote for review or prepare a proposal only if the accepted product rules allow it; final confirmation remains a dedicated UI action handled by Task 33.
- Tool-call budget, input validation, ownership/state gates, stale-quote detection, and refresh behavior.
- Chat/booking UI query synchronization.
- Evaluation fixtures for attempts to auto-confirm, hidden fees, expired quotes, unsupported supplier requests, malicious tool args, provider failure, and ambiguous user consent.

## Do not

- Do not expose a confirmation/payment tool to the model.
- Do not infer payment consent from conversational language.
- Do not claim a booking is confirmed before the authoritative booking state says so.

## Definition of Done

- Chat safely searches and explains real/stub quote records.
- Confirmation always exits chat into explicit C4 UI.
- State/source/expiry details stay synchronized.
- Auto-book attempts are rejected and tested.

## Handoff

Document tool schema/gate, quote-summary rules, navigation event, forbidden actions, and evaluation outcomes.

## Suggested branch and commit

- Branch: `agent/task-35-booking-chat`
- Commit: `feat: add safe booking quote chat tools`
