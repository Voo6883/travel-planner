# Task 09 — Mailer and Account Lifecycle

## Objective

Implement transactional mail behind `MailerPort` and complete local account verification, password, and deletion workflows.

## Dependencies

- Task 08 complete.

## Required reading

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
