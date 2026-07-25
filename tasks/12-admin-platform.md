# Task 12 — Admin Platform

## Objective

Implement the planned development/admin account-management slice with strict authorization and auditability.

## Dependencies

- Tasks 07, 08, 09, and 11 complete.

## Required reading

- **[`docs/adr/009-session-lifecycle-revocation.md`](../docs/adr/009-session-lifecycle-revocation.md) — Accepted; extends ADR 002/004 and overrides this brief where they differ**
- `plans/superpower/PLAN.md` §4.0.6
- `plans/USE-CASES.md` UC-A15 and UC-A16
- `plans/BACKLOG.md` S2-1, S2-2, S2-11
- `docs/UI-UX-DESIGN-SYSTEM.md` admin density rules

## Scope

### Backend

- Dev/Docker/local-only ADMIN seed using an idempotent and profile-gated mechanism.
- Store role value `ADMIN`; expose Spring authority `ROLE_ADMIN`; enforce with `hasRole('ADMIN')`.
- Admin APIs for paginated user list, user detail, enable/disable, and password reset.
- **Admin actions must terminate sessions (ADR 009):** disable-user and reset-password bump `user.token_version`. Without it a disabled or compromised account keeps working until its token expires — unacceptable for an account that can spend money.
- Do not grant cross-user trip access.
- Audit every admin mutation with actor, target, action, time, and result.
- Typed `forbidden`, `user_not_found`, conflict, and validation errors.

### Frontend

- `(admin)` route shell and role guard.
- User list/detail pages using generated API types.
- Enable/disable and reset-password flows with explicit confirmation and complete pending/error/success states.
- No ADMIN credentials displayed outside clearly marked local-development documentation.

### Tests

- Profile tests proving the seed is absent in production.
- Authorization tests for unauthenticated, user, and admin roles.
- Audit persistence and transaction rollback tests.
- Frontend route-guard and action tests.

## Do not

- Do not allow admins to inspect trips, chats, bookings, or LLM content.
- Do not store the plaintext seed password in migration output or logs.
- Do not enable the seed in production.

## Definition of Done

- Admin account management works only for authorized admins.
- Every mutation is audited.
- Production cannot create the development seed.
- Frontend and backend tests cover authorization boundaries.

## Handoff

Document role translation, seed profile, reset semantics, audit schema, and admin API routes.

## Suggested branch and commit

- Branch: `agent/task-12-admin-platform`
- Commit: `feat: implement audited admin platform`
