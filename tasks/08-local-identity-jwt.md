# Task 08 — Local Identity and JWT Session

## Objective

Implement secure local registration/login and the common session model used by every identity provider.

## Dependencies

- Tasks 06 and 07 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0.5 and security requirements
- `docs/adr/002-jwt-cookie.md`
- `plans/USE-CASES.md` UC-A01, A04, A08, A10, A11
- `plans/BACKLOG.md` S1-6

## Scope

- `IdentityProviderPort` and local password adapter.
- Register with email, username, and password.
- Login using email or case-insensitive username.
- BCrypt password hashing at the planned strength.
- Account status and email-verification fields.
- Self-issued JWT in `httpOnly`, `Secure` where appropriate, `SameSite=Lax` cookie.
- Logout and `GET /api/v1/auth/me`.
- `UserContext` creation for authenticated requests.
- User-scoped authorization foundation.
- Failed-login tracking and bounded lockout behavior.
- CSRF strategy compatible with cookie authentication.
- Typed errors without exposing account-existence or password details.
- OpenAPI updates, generated client update, backend tests, and minimal API-level integration tests.

## Do not

- Do not implement Firebase, GitHub OAuth, Resend, password reset mail, or frontend forms in this task.
- Do not store plaintext passwords or JWT secrets.
- Do not trust user IDs from request bodies for ownership.

## Validation

Test register, duplicate email/username, login by both identifiers, invalid password, lockout, logout, expired/invalid token, unverified-user gate, and `auth/me`.

```bash
cd apps/backend && ./gradlew test
npm run codegen
```

## Definition of Done

- Local identity works end-to-end at API level.
- Session cookie attributes are covered by tests.
- User context is available to later services.
- Authorization failures use standard error codes.

## Handoff

Document token claims, cookie settings by profile, password rules, and extension points for Tasks 09–10.

## Suggested branch and commit

- Branch: `agent/task-08-local-identity`
- Commit: `feat: implement local identity and JWT session`
