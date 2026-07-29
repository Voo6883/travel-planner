import type { components } from '@/generated/api/schema';
import { apiBaseUrl, apiRequest } from './client';
import {
  acceptedResponseSchema,
  authSessionResponseSchema,
  currentUserSchema,
  registrationResponseSchema,
} from './schemas/auth.schema';

/**
 * Account endpoints (UC-A01–A14). Every type below is an alias of the generated contract — never
 * a re-declaration of it (PLAN §4.2.6-A, `docs/AGENT-HARNESS.md` §4).
 *
 * Pure async functions, no React (PLAN §4.2.6-H). Hooks in `features/auth/hooks/` call these.
 */

export type RegisterRequest = components['schemas']['RegisterRequest'];
export type LoginRequest = components['schemas']['LoginRequest'];
export type EmailOnlyRequest = components['schemas']['EmailOnlyRequest'];
export type ConfirmEmailVerificationRequest = components['schemas']['ConfirmEmailVerificationRequest'];
export type ResetPasswordRequest = components['schemas']['ResetPasswordRequest'];
export type ChangePasswordRequest = components['schemas']['ChangePasswordRequest'];
export type FirebaseAuthRequest = components['schemas']['FirebaseAuthRequest'];
export type LinkProviderRequest = components['schemas']['LinkProviderRequest'];

export type CurrentUser = components['schemas']['CurrentUser'];
export type AuthSessionResponse = components['schemas']['AuthSessionResponse'];
export type RegistrationResponse = components['schemas']['RegistrationResponse'];
export type AcceptedResponse = components['schemas']['AcceptedResponse'];
export type IdentityProvider = CurrentUser['linked_providers'][number];

/** UC-A01. Always `PENDING_VERIFICATION`, whether or not the address was already registered. */
export async function register(body: RegisterRequest): Promise<RegistrationResponse> {
  return apiRequest({
    path: '/auth/register',
    method: 'POST',
    body,
    validate: (payload) => registrationResponseSchema.parse(payload),
  });
}

/** UC-A04. `login` accepts an email *or* a username; the server disambiguates on `@`. */
export async function login(body: LoginRequest): Promise<AuthSessionResponse> {
  return apiRequest({
    path: '/auth/login',
    method: 'POST',
    body,
    validate: (payload) => authSessionResponseSchema.parse(payload),
  });
}

/** UC-A02 / UC-A05. One endpoint for Google sign-up and sign-in; `is_new_user` says which. */
export async function authenticateWithFirebase(body: FirebaseAuthRequest): Promise<AuthSessionResponse> {
  return apiRequest({
    path: '/auth/firebase',
    method: 'POST',
    body,
    validate: (payload) => authSessionResponseSchema.parse(payload),
  });
}

/** UC-A11. The single source for roles, `email_verified`, and linked providers. */
export async function fetchCurrentUser(signal?: AbortSignal): Promise<CurrentUser> {
  return apiRequest({
    path: '/auth/me',
    signal,
    validate: (payload) => currentUserSchema.parse(payload),
  });
}

/** UC-A10. Public and idempotent — it must work after the access token has already expired. */
export async function logout(): Promise<void> {
  await apiRequest<void>({ path: '/auth/logout', method: 'POST' });
}

/** ADR 009 §5. Bumps `token_version`, so tokens already in flight stop working immediately. */
export async function logoutEverywhere(): Promise<void> {
  await apiRequest<void>({ path: '/auth/logout-all', method: 'POST' });
}

/** UC-A07. `202 ACCEPTED` for a registered address, an unregistered one, and a provider-only one. */
export async function requestPasswordReset(body: EmailOnlyRequest): Promise<AcceptedResponse> {
  return apiRequest({
    path: '/auth/password/forgot',
    method: 'POST',
    body,
    validate: (payload) => acceptedResponseSchema.parse(payload),
  });
}

/** UC-A07. Consumes the mailed token and terminates every session for the account. */
export async function resetPassword(body: ResetPasswordRequest): Promise<void> {
  await apiRequest<void>({ path: '/auth/password/reset', method: 'POST', body });
}

/** UC-A12. Ends every session including this one, so the caller signs in again afterwards. */
export async function changePassword(body: ChangePasswordRequest): Promise<void> {
  await apiRequest<void>({ path: '/auth/password', method: 'PUT', body });
}

/** UC-A08. Public — the whole point is that the account cannot sign in yet. */
export async function confirmEmailVerification(body: ConfirmEmailVerificationRequest): Promise<void> {
  await apiRequest<void>({ path: '/auth/verify-email/confirm', method: 'POST', body });
}

/** UC-A13. Same constant `202` as forgot-password, and rate-limited the same way. */
export async function resendEmailVerification(body: EmailOnlyRequest): Promise<AcceptedResponse> {
  return apiRequest({
    path: '/auth/verify-email/resend',
    method: 'POST',
    body,
    validate: (payload) => acceptedResponseSchema.parse(payload),
  });
}

export interface LinkProviderCommand {
  provider: Exclude<IdentityProvider, 'GITHUB' | 'LOCAL'>;
  credential: string;
}

/**
 * UC-A09. The explicit confirmation ADR 009 §4 requires when auto-linking is refused.
 *
 * `GITHUB` is excluded at the type level, not by a runtime check: its credential is a single-use
 * authorization code the browser never holds, so linking it is a redirect
 * (`githubOAuthStartUrl({ mode: 'link' })`), never a request body.
 */
export async function linkProvider(command: LinkProviderCommand): Promise<void> {
  await apiRequest<void>({
    // The contract publishes one templated path; the segment is a closed enum, not user input.
    path: `/auth/providers/${command.provider}` as '/auth/providers/{provider}',
    method: 'POST',
    body: { credential: command.credential } satisfies LinkProviderRequest,
  });
}

/** ADR 009 §4. Refused when it would leave the account with no way in; ends every session. */
export async function unlinkProvider(provider: IdentityProvider): Promise<void> {
  await apiRequest<void>({
    path: `/auth/providers/${provider}` as '/auth/providers/{provider}',
    method: 'DELETE',
  });
}

/** UC-A14. Soft delete with PII anonymisation; irreversible from the API. */
export async function deleteCurrentUser(): Promise<void> {
  await apiRequest<void>({ path: '/auth/me', method: 'DELETE' });
}

export type GithubOAuthMode = 'sign_in' | 'link';

/**
 * UC-A03 / UC-A06. The URL for a **browser navigation**, not a `fetch`.
 *
 * GitHub answers with a 302 to its own authorize page and expects the user's browser to follow
 * it, then sets an `httpOnly` state cookie the callback must see. An XHR would follow the
 * redirect invisibly, land on github.com under our origin's CORS rules, and fail — and even if it
 * succeeded the user would never see the consent screen.
 *
 * Built from `apiBaseUrl()` so it goes through the same-origin proxy (ADR 006) and the state
 * cookie is first-party like every other cookie in the flow.
 */
export function githubOAuthStartUrl(mode: GithubOAuthMode = 'sign_in'): string {
  return `${apiBaseUrl()}/auth/oauth/github/start?mode=${mode}`;
}
