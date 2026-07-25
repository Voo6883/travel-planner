# ADR 009: Session lifecycle, revocation, and account linking safety

## Status

Accepted — extends [ADR 002](002-jwt-auth.md) (transport unchanged) and
[ADR 004](004-multi-provider-auth-resend.md) (linking rules tightened).

## Context

[ADR 002](002-jwt-auth.md) issues a stateless self-signed JWT and defers refresh to "v1.1".
`PLAN.md` §4.0.5 omits `POST /auth/refresh` from the authoritative endpoint table entirely, and
defines logout as "clear the session cookie".

Clearing a cookie is not revocation. Four locked features silently depend on revocation that does
not exist:

| Feature | Current behaviour |
|---|---|
| Admin **disable user** (§4.0.6) | User keeps working until the token expires |
| Admin **reset password** (§4.0.6) | Existing sessions survive |
| User **change password** (UC-A12) | The "I was compromised" case fails |
| **Delete account** (UC-A14, soft-delete) | The deleted account's token still authenticates |

Two further gaps compound this:

- **Token staleness.** `UserContext` is `{ userId, email, roles[] }` with no `emailVerified`, but
  UC-A08 blocks the planner until the email is verified — so the gate needs either a per-request
  lookup or a claim that goes stale.
- **Account-linking pre-hijack.** §4.0.5 auto-links a provider whose email matches an existing
  account, requiring only that the *incoming* email be verified. An attacker registers locally with
  `victim@gmail.com`; the victim later signs in with Google and is auto-linked **into the
  attacker's row**, leaving the attacker with password access to the victim's trips.

## Decision

### 1. Server-side revocation via token version

Add to `user`:

| Column | Purpose |
|---|---|
| `token_version integer not null default 0` | Bumped to revoke all sessions |
| `sessions_valid_after timestamptz null` | Rejects tokens issued before this instant |

The JWT carries `tv` (token version) and standard `iat`. The authentication filter rejects the
token when `tv != user.token_version` **or** `iat < sessions_valid_after`.

**Bump `token_version` on:** password change, admin password reset, admin disable, account delete,
provider unlink, and explicit "log out of all devices".

### 2. Accept the per-request user lookup

Revocation requires reading current user state per request. This is one indexed primary-key lookup;
at the §14 target of ~50 concurrent users it is negligible, and it also resolves the
`email_verified` staleness problem by making user state authoritative on every request rather than
frozen into a claim.

A short-TTL (≈30 s) in-process cache may be added **only** if profiling shows a need, and must be
invalidated on `token_version` bump. Do not introduce Redis for this in v1.

### 3. Token lifetime and refresh

| Item | Value |
|---|---|
| Access token TTL | **30 minutes** |
| Refresh token | httpOnly cookie, **14 days**, **rotated on every use** |
| Refresh endpoint | `POST /api/v1/auth/refresh` — **added to the §4.0.5 endpoint table** |
| Reuse detection | A rotated-and-reused refresh token bumps `token_version` (revokes the family) and logs a security event |
| Logout | Clears both cookies **and** invalidates the refresh token server-side |

Refresh tokens are stored hashed in a `refresh_token` table with `user_id`, `expires_at`,
`rotated_at`, `revoked_at`.

**Long operations must not fail on expiry.** The 90 s research job (§14) runs server-side under the
job's own authorisation and does not re-check the caller's token mid-run. Chat SSE streams
([ADR 007](007-chat-streaming-transport.md)) are authorised at connect; the client refreshes before
opening a new stream, not during one.

### 4. Account linking requires proof, not email equality

| Rule | Detail |
|---|---|
| Auto-link permitted only when | The **existing** account has `email_verified=true` **and** the incoming provider email is verified |
| Otherwise | Require an explicit, authenticated "link this provider?" confirmation while signed in to the existing account |
| GitHub | Use the **primary AND verified** email only; ignore `@users.noreply.github.com` and unverified addresses |
| Firebase | Assert `firebase.sign_in_provider == 'google.com'` **and** `aud == <project_id>` — otherwise enabling Email/Password in the Firebase console opens an unvetted registration path |
| OAuth-only accounts | `password_hash IS NULL` → `POST /auth/password/forgot` must **not** mint a local password; return the same generic response and send a "sign in with your provider" email |
| Unlink | `DELETE /api/v1/auth/providers/{provider}` — refused if it would leave the account with no usable sign-in method; bumps `token_version` |
| Username vs email | A username **may not contain `@`**; both columns get case-insensitive unique indexes |

### 5. Missing endpoints added to the contract

`POST /auth/refresh`, `POST /auth/verify-email/confirm`, `DELETE /auth/providers/{provider}`,
`POST /auth/logout-all`. OpenAPI-first means the §4.0.5 table is the spec; these were absent.

### 6. Brute-force and enumeration

| Control | Rule |
|---|---|
| Lockout | 5 failures / 15 min, keyed on **both** username and client IP — username-only is a trivial targeted DoS |
| Storage | Database-backed in v1 (in-memory breaks the locked stateless/multi-instance goal) |
| Rate limit | `POST /auth/password/forgot` and `/auth/verify-email/resend` limited per email and per IP |
| Uniform responses | Register, forgot-password, and resend return identical responses whether or not the account exists |

## Rationale

| Factor | Token version (chosen) | Pure stateless JWT | Server-side sessions |
|---|---|---|---|
| Immediate revocation | Yes | **No** | Yes |
| Extra infrastructure | None | None | Redis / session store |
| Per-request cost | One PK lookup | Zero | One store lookup |
| Fixes `email_verified` staleness | Yes | No | Yes |
| Retrofit cost if deferred | **High** — touches every auth path | — | High |

Deferring this is what makes it expensive: the columns are free to add now and painful to add after
admin, mailer, and three identity adapters are built against a stateless assumption.

## Consequences

- Flyway adds `token_version` + `sessions_valid_after` to `user`, and a `refresh_token` table.
- The JWT filter becomes user-state-aware; ADR 002's "stateless enough for v1" framing is narrowed
  to "stateless transport, authoritative user state".
- Admin disable/reset and self-service password change gain an explicit "sessions terminated"
  assertion in their integration tests.
- Cross-user isolation and revocation both need explicit tests — neither is covered by ArchUnit.
- Tasks affected: **08** (JWT session — now owns revocation), **09** (account lifecycle),
  **10** (external identity providers — linking rules), **12** (admin — must terminate sessions).

## Alternatives considered

- **Pure stateless JWT with short TTL only** — rejected; a 30-minute window of valid access for a
  disabled or compromised account is not acceptable for an account that can spend money.
- **Redis denylist of revoked token IDs** — rejected for v1; adds a runtime dependency (§4.0.9,
  Redis not scheduled until Sprint 8) to solve what one integer column solves.
- **Full server-side sessions** — rejected by [ADR 002](002-jwt-auth.md); no reason to reopen.
- **Email equality as sufficient proof for linking** — rejected; it is the documented pre-hijack
  takeover pattern.
