# Task 09 — Mailer and Account Lifecycle

## Objective

Implement transactional mail behind `MailerPort` and complete local account verification, password, and deletion workflows.

## Dependencies

- Task 08 complete.

## Required reading

- **[`docs/adr/009-session-lifecycle-revocation.md`](../docs/adr/009-session-lifecycle-revocation.md) — Accepted; extends ADR 002/004 and overrides this brief where they differ**
- `plans/superpower/PLAN.md` §4.0.10
- `docs/adr/004-multi-provider-auth-resend.md`
- `plans/USE-CASES.md` UC-A07, A08, A12–A14 and UC-N01–N03
- `plans/BACKLOG.md` S1-6d and S1-6e

## Scope

- `MailerPort`, typed `MailMessage`, and mail-template boundary.
- `StubMailerAdapter` as default local/test provider.
- Resend adapter isolated in infrastructure.
- Welcome, verify-email, password-reset, and reset-confirmation templates as required by the plan.
- Signed/single-use verification and reset tokens stored hashed with expiry.
- Verify email endpoint and resend verification.
- Forgot/reset password.
- Change password for authenticated users.
- Soft-delete account and anonymize PII while preserving required audit/referential integrity.
- **Session revocation on every credential-changing action (ADR 009 §1):** password change, password reset, and account delete must bump `user.token_version`. Clearing a cookie is not revocation — without the bump, the "I was compromised" case silently fails and a deleted account's token still authenticates.
- `POST /api/v1/auth/verify-email/confirm` and `POST /api/v1/auth/logout-all` added to the contract (ADR 009 §5).
- OAuth-only accounts (`password_hash IS NULL`): `POST /auth/password/forgot` must **not** mint a local password. Return the same generic response and send a "sign in with your provider" email.
- Rate-limit `forgot` and `verify-email/resend` per email **and** per IP; responses identical whether or not the account exists.
- Mail audit events without message bodies or raw recipient PII in logs.
- Retry/failure handling that does not leave invalid account state.
- OpenAPI/client updates and backend tests with the stub provider.

## Do not

- Do not call Resend from the frontend.
- Do not make live Resend credentials mandatory in development or CI.
- Do not log reset/verification tokens.
- Do not implement OAuth providers yet.

## Validation

Test token expiry, token reuse, invalid token, resend limits, password change, reset, account deletion/anonymization, and stub-mail contents.

```bash
cd apps/backend && ./gradlew test
npm run codegen
```

Optionally run a manually approved Resend smoke test outside CI using a verified sender.

## Definition of Done

- All account-lifecycle APIs work with the stub mailer.
- Live adapter is configuration-controlled.
- Secrets and PII remain protected.
- Local sign-up verification gate is enforceable.

## Handoff

Report templates, environment variables, provider switch, token lifetimes, and account-deletion semantics.

## Suggested branch and commit

- Branch: `agent/task-09-mail-account-lifecycle`
- Commit: `feat: add mailer and account lifecycle`
