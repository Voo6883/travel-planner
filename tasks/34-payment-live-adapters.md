# Task 34 — Payment and Live Adapter Slots

## Objective

Implement the payment abstraction and production-ready supplier adapter slots while retaining stub-first operation until vendors are selected.

## Dependencies

- Tasks 32 and 33 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0.7 and §7
- `plans/BACKLOG.md` S7-2
- Relevant security and configuration sections

## Scope

- `PaymentPort`/provider-neutral checkout or tokenized redirect contract.
- Stub payment adapter covering success, decline, timeout, duplicate callback, cancellation, and reconciliation.
- Store provider/payment references and safe status metadata only; no raw card/PAN/CVV.
- Signed callback/webhook verification interface if the selected provider requires it; actual provider implementation only when vendor/configuration is explicitly approved.
- Live flight/hotel/payment adapter package skeletons or implementations behind configuration, sharing port contract tests with stubs.
- Credential/config validation, timeouts, retries appropriate to idempotent operations, circuit-breaker boundaries, structured errors, PII-safe logs, and audit correlation.
- Reconciliation job/service for ambiguous provider/local outcomes.
- Deployment documentation for provider secrets and callback URLs.

## Do not

- Do not invent vendor APIs or implement against an unspecified supplier.
- Do not expose secrets in frontend/build artifacts.
- Do not retry non-idempotent provider actions blindly.
- Do not remove stub mode.

## Validation

Run shared adapter contracts, callback-signature tests, ambiguous-timeout/reconciliation scenarios, secret scanning, and full confirmation flow with stub payment.

## Definition of Done

- Payment/supplier integration boundaries are safe and replaceable.
- Stub mode exercises all confirmation outcomes.
- No sensitive card data enters the system.
- Live adapter activation is explicit and documented.

## Handoff

Record selected/unselected providers, config keys, callback verification, reconciliation procedure, and production activation checklist.

## Suggested branch and commit

- Branch: `agent/task-34-payment-adapters`
- Commit: `feat: add payment and live adapter boundaries`
