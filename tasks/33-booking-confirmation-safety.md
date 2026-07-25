# Task 33 — Booking Confirmation Safety

## Objective

Implement explicit, idempotent, audited booking confirmation with price revalidation and no LLM authority.

## Dependencies

- Task 32 complete.

## Required reading

- `plans/superpower/PLAN.md` §6.1 idempotency and §7
- `plans/USE-CASES.md` UC-C4-03–C4-05
- `plans/BACKLOG.md` S7-1, S7-3, S7-4

## Scope

- Confirmation command requiring authenticated user, booking/quote ID, current quote version, and `Idempotency-Key`.
- Persist idempotency record/result for the planned retention window; identical retry returns the same result, conflicting reuse fails safely.
- Revalidate quote price, availability, expiry, currency, and terms through the supplier/booking port before commit.
- Explicit UI confirmation showing final price and key terms; no optimistic success.
- State transitions to `CONFIRMED`, `FAILED`, or `CANCELLED` as accepted, with complete history and audit event.
- Transaction boundary preventing partial confirmation/history/payment-reference writes.
- Typed errors including expired/changed quote, unavailable, duplicate/conflicting key, provider declined, forbidden, and invalid state.
- Confirmation receipt/provider-reference view and optional notification hook without requiring live mail.
- Tests for duplicate requests, concurrent confirmation, price change, timeout after provider success, retry recovery, rollback, and authorization.

## Do not

- Do not confirm from chat/model output.
- Do not store raw PAN/card data.
- Do not mark success before provider result and local commit are reconciled.
- Do not silently accept a changed final price.

## Definition of Done

- User confirmation is explicit and reviewable.
- Retries cannot double-book.
- Price/terms are revalidated.
- Every transition is audited and recoverable.

## Handoff

Document idempotency storage/retention, reconciliation rules, confirmation API/UI contract, and payment/provider interface needs.

## Suggested branch and commit

- Branch: `agent/task-33-booking-confirmation`
- Commit: `feat: add safe idempotent booking confirmation`
