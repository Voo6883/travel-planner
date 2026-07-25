# Task 10 — Firebase and GitHub Identity Providers

## Objective

Add Gmail/Google and GitHub sign-up/login adapters while preserving the common user/session model from Task 08.

## Dependencies

- Tasks 08 and 09 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0.5
- `docs/adr/004-multi-provider-auth-resend.md`
- `plans/USE-CASES.md` UC-A02, A03, A05, A06, A09
- `plans/BACKLOG.md` S1-6b and S1-6c

## Scope

### Firebase Google

- Verify Firebase ID tokens server-side through a dedicated adapter.
- Create a new user/identity on first sign-in and return `is_new_user` behavior.
- Mark provider-verified email as verified.
- Send welcome mail through `MailerPort` only after successful first-time account creation.

### GitHub OAuth

- OAuth start, callback, state validation, and secure error handling.
- Exchange provider authorization code only in infrastructure.
- Normalize provider claims to the internal identity model.
- Issue the same internal JWT session used by local login.

### Account linking

- Link a provider to an existing user only under the documented verified-email rules.
- Prevent one external identity from linking to multiple users.
- Handle missing/private provider email and identity conflicts with typed errors.
- Audit security-sensitive linking events.

### Contract and tests

- OpenAPI updates and generated frontend client.
- Stub/fake provider tests for CI.
- Integration tests for first login, returning login, linking, conflict, invalid state, invalid token, and provider outage.

## Do not

- Do not issue Firebase/GitHub tokens as the application session.
- Do not expose provider secrets to the browser.
- Do not rely on a live provider in CI.
- Do not implement frontend login controls; Task 11 owns UI integration.

## Definition of Done

- All three providers produce one internal user/session model.
- Linking and conflict behavior is deterministic and tested.
- Provider SDK/types stay inside their adapters.
- Welcome mail is sent once for new users only.

## Handoff

Document callback URLs, required env vars, claim mapping, linking rules, and frontend endpoints.

## Suggested branch and commit

- Branch: `agent/task-10-external-identity`
- Commit: `feat: add Firebase and GitHub identity providers`
