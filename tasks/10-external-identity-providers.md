# Task 10 — Firebase and GitHub Identity Providers

## Objective

Add Gmail/Google and GitHub sign-up/login adapters while preserving the common user/session model from Task 08.

## Dependencies

- Tasks 08 and 09 complete.

## Required reading

- **[`docs/adr/009-session-lifecycle-revocation.md`](../docs/adr/009-session-lifecycle-revocation.md) — Accepted; extends ADR 002/004 and overrides this brief where they differ**
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

### Account linking — ADR 009 §4 overrides the original rule

> **Email equality is not proof of ownership.** The pre-ADR rule required only that the *incoming*
> provider email be verified. That is the documented pre-hijack takeover pattern: an attacker
> registers locally as `victim@gmail.com`, the victim later signs in with Google and is auto-linked
> **into the attacker's account**, leaving the attacker with password access to the victim's trips.

- Auto-link permitted **only** when the **existing** account has `email_verified = true` **and** the incoming provider email is verified.
- Otherwise require an explicit, authenticated "link this provider?" confirmation while signed in to the existing account.
- **GitHub:** use the **primary AND verified** email only. Ignore `@users.noreply.github.com` and any unverified address.
- **Firebase:** assert `firebase.sign_in_provider == 'google.com'` **and** `aud == <project_id>` — otherwise enabling Email/Password in the Firebase console opens an unvetted registration path into this system.
- `DELETE /api/v1/auth/providers/{provider}` to unlink; refused if it would leave the account with no usable sign-in method; bumps `token_version`.
- A username may not contain `@`; both username and email get case-insensitive unique indexes.
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
