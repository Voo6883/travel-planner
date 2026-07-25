# ADR 004: Multi-provider authentication and Resend mailer

## Status

Accepted — supersedes the OAuth deferral in ADR 002 (JWT session transport unchanged).

## Context

v1 requires three sign-in methods:

1. **Email or username + password** (local)
2. **Gmail sign-up & sign-in** via Firebase Authentication (Google provider)
3. **GitHub OAuth**

Transactional email (welcome, verify, password reset) uses **Resend** (`resend.com`).

ADR 002 locked JWT httpOnly cookies as session transport. This ADR locks **identity
providers** and **mail delivery** while keeping `UserContext` and cookie sessions.

## Decision

### Identity — port + adapter pattern (§4.0.5)

```
IdentityProviderPort  →  LocalPassword | Firebase | GitHub adapters
         ↓
    AuthService  →  JwtTokenService  →  httpOnly cookie (ADR 002)
```

| Provider | Enum | Client flow | Backend |
|---|---|---|---|
| Local | `LOCAL` | Form: email/username + password | BCrypt verify |
| Google / Gmail | `FIREBASE_GOOGLE` | `GmailSignInButton` on login **and** register → `idToken` | Verify token; create user if new; welcome email |
| GitHub | `GITHUB` | OAuth2 redirect | Code exchange + user API |

- One `user` row per person; multiple `user_identity` rows per linked provider.
- Account linking by verified email when safe.

### Session

- Unchanged from ADR 002: self-issued JWT in `tp_session` httpOnly cookie.
- All three providers converge to the same session after successful authentication.

### Mailer — Resend (§4.0.10)

```
MailerPort  →  ResendMailerAdapter  (production)
            →  StubMailerAdapter    (dev, mailer.provider=stub)
```

| Email | When |
|---|---|
| Welcome | After local register **or** first Gmail sign-up (`is_new_user`) |
| Verify email | Registration (signed link) |
| Password reset | Forgot password flow |

- `RESEND_API_KEY` and `MAIL_FROM` via environment.
- Resend SDK/HTTP **only** in `infrastructure/mail/`.

### Frontend

- `features/auth/` — `GmailSignInButton` on **login and register** pages; shared `use-firebase-google-auth` hook.
- `NEXT_PUBLIC_FIREBASE_*` for Firebase client config only.
- No Resend or GitHub secret in browser.

## Rationale

| Factor | Multi-provider port | Single local-only |
|---|---|---|
| User choice | Gmail / GitHub / password | Limited |
| Extensibility | Add provider = new adapter | Forking AuthService |
| Testability | Mock `IdentityProviderPort` | Same |
| Industry pattern | Same as Stripe/OAuth aggregators | Simpler but insufficient |

Firebase for Google: managed Google OAuth, mobile-ready later, ID token verification on backend.

Resend: modern transactional API, simple HTML templates, good DX for v1.

## Security

- Firebase: verify `idToken` server-side; never trust client claims alone.
- GitHub: `state` param CSRF protection on OAuth; validate callback.
- Password reset: single-use tokens, 1h expiry, hashed at rest.
- Rate-limit `/auth/*` endpoints (§14).
- CSRF on cookie session for mutating requests (§4.0.9).

## Consequences

- Phase 0b Sprint 1–2 includes auth adapters + Resend + `features/auth/`.
- `.env.example` documents all auth and mail keys.
- ADR 002 remains valid for **session transport**; OAuth deferral is withdrawn.

## Env vars

```bash
# Local JWT
JWT_SECRET=...

# Firebase (Google)
FIREBASE_PROJECT_ID=...
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json

# GitHub OAuth
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
GITHUB_CALLBACK_URL=http://localhost:8080/api/v1/auth/oauth/github/callback

# Resend
RESEND_API_KEY=re_...
MAIL_FROM=noreply@yourdomain.com
MAILER_PROVIDER=resend

# Firebase client (frontend — public)
NEXT_PUBLIC_FIREBASE_API_KEY=...
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=...
NEXT_PUBLIC_FIREBASE_PROJECT_ID=...
```

## Alternatives considered

- **Spring Security OAuth2 only (no Firebase)** — rejected; user specified Firebase for Gmail.
- **SendGrid / AWS SES** — rejected; user specified Resend.
- **Magic link only** — rejected; password + social required.
