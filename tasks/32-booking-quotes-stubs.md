# Task 32 — Booking Quote Domain and Stub Suppliers

## Objective

Implement the safe booking search/quote foundation with realistic stub flight and hotel suppliers before any live booking integration.

## Dependencies

- Tasks 07, 15, and 31 complete.

## Required reading

- `plans/superpower/PLAN.md` §7 and stub-first policy
- `plans/USE-CASES.md` UC-C4-01, C4-02
- `plans/BACKLOG.md` S6-1 through S6-4

## Scope

### Domain/data

- Booking aggregate and distinct `booking.status` state machine beginning `DRAFT → QUOTED → HELD`, separate from `trip.status`.
- Quote/search request, flight/hotel option, price/fees/currency, terms, supplier reference, expiry, cancellation/refund flags, and provenance types.
- Migrations for booking, quote/version/history, and supplier references needed by stub flow.
- User/trip ownership and prerequisite `ITINERARY_READY` gate.

### Suppliers/application

- Flight and hotel search ports.
- Realistic deterministic `StubFlightSearchAdapter` and `StubHotelSearchAdapter` with contract fixtures, latency/error/empty/expired scenarios, and configuration switch.
- Search/quote application services outside long transactions; persist validated quotes and transition state safely.
- Search and quote APIs with typed errors.

### Frontend

- `features/booking/` browse experience for flights/hotels, quote price/fees/terms/expiry, filters required by accepted use cases, compare/select/hold preparation, and complete loading/error/empty/expired states.
- Clearly label stub/demo supplier data in non-production profiles.

## Do not

- Do not confirm bookings, charge payments, or imply stub quotes are live.
- Do not allow the LLM to mutate booking state.
- Do not store card data.

## Definition of Done

- User can search and compare deterministic stub quotes.
- Booking and Trip statuses remain correctly namespaced.
- Supplier ports have shared contract tests.
- Quote expiry and error states are explicit.

## Handoff

Document booking schema/state machine, supplier contracts, fixtures, quote expiry, and confirmation inputs for Task 33.

## Suggested branch and commit

- Branch: `agent/task-32-booking-quotes`
- Commit: `feat: add booking quotes with stub suppliers`
