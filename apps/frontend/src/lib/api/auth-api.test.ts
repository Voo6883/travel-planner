import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockContract } from '@/test/contract-mock';
import { ApiError } from './api-error';
import {
  authenticateWithFirebase,
  fetchCurrentUser,
  githubOAuthStartUrl,
  linkProvider,
  login,
  register,
  requestPasswordReset,
  unlinkProvider,
} from './auth-api';

const VERIFIED_USER = {
  user_id: '6f9619ff-8b86-d011-b42d-00c04fc964ff',
  email: 'aisyah@example.com',
  username: 'aisyah',
  roles: ['USER'],
  email_verified: true,
  linked_providers: ['LOCAL'],
};

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('auth-api', () => {
  it('sends every account call to the same-origin proxy with cookie credentials (ADR 006)', async () => {
    const contract = mockContract({ '/auth/login': { body: sessionBody() } });

    await login({ login: 'aisyah', password: 'correct-horse-battery' });

    const call = contract.lastCall();
    expect(call.url).toBe('/api/v1/auth/login');
    // The whole reason the proxy exists: `SameSite=Lax` only travels first-party.
    expect(call.url).not.toMatch(/^https?:/);
    expect(call.credentials).toBe('include');
    expect(call.method).toBe('POST');
  });

  it('validates the session payload at the boundary instead of trusting it', async () => {
    // Everything downstream of this response is an authorisation decision, so a missing
    // `email_verified` must fail loudly rather than default to a falsy value.
    mockContract({ '/auth/login': { body: { user: { email: 'aisyah@example.com' } } } });

    await expect(login({ login: 'aisyah', password: 'x' })).rejects.toThrow();
  });

  it('rejects a /auth/me payload whose roles are missing', async () => {
    mockContract({ '/auth/me': { body: { ...VERIFIED_USER, roles: undefined } } });

    await expect(fetchCurrentUser()).rejects.toThrow();
  });

  it('returns the constant registration status without inspecting anything else', async () => {
    mockContract({ '/auth/register': { body: { status: 'PENDING_VERIFICATION' } } });

    const response = await register({
      email: 'aisyah@example.com',
      username: 'aisyah',
      password: 'correct-horse-battery',
    });

    expect(response.status).toBe('PENDING_VERIFICATION');
  });

  it('translates a rate-limited forgot-password into a typed error carrying the wait', async () => {
    mockContract({
      '/auth/password/forgot': {
        status: 429,
        body: { code: 'rate_limited', message: 'Too many.', details: { retry_after_seconds: 3600 } },
      },
    });

    const error = (await requestPasswordReset({ email: 'a@example.com' }).catch(
      (thrown) => thrown,
    )) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('rate_limited');
    expect(error.retryAfterSeconds).toBe(3600);
  });

  it('exposes the provider behind a link-required conflict so the UI can name it', async () => {
    mockContract({
      '/auth/firebase': {
        status: 409,
        body: {
          code: 'provider_link_required',
          message: 'Sign in first.',
          details: { provider: 'FIREBASE_GOOGLE' },
        },
      },
    });

    const error = (await authenticateWithFirebase({ id_token: 'x'.repeat(24) }).catch(
      (thrown) => thrown,
    )) as ApiError;

    expect(error.code).toBe('provider_link_required');
    // Without this the recovery is a riddle: "connect which provider?"
    expect(error.linkRequiredProvider).toBe('FIREBASE_GOOGLE');
  });

  it('builds the provider path from the enum segment', async () => {
    // Stubbed in its contract form, so the key stays a member of the generated path union.
    const contract = mockContract({ '/auth/providers/{provider}': { status: 204 } });

    await linkProvider({ provider: 'FIREBASE_GOOGLE', credential: 'firebase-id-token' });
    expect(contract.lastCall().url).toBe('/api/v1/auth/providers/FIREBASE_GOOGLE');

    await unlinkProvider('GITHUB');
    expect(contract.lastCall().url).toBe('/api/v1/auth/providers/GITHUB');
    expect(contract.lastCall().method).toBe('DELETE');
  });
});

/**
 * GitHub sign-in is a navigation, not a request, so the only thing to assert is the URL the
 * button will send the browser to — and that it stays on this origin so the `tp_oauth_state`
 * cookie the callback checks is first-party.
 */
describe('githubOAuthStartUrl', () => {
  it('is same-origin and defaults to sign-in', () => {
    expect(githubOAuthStartUrl()).toBe('/api/v1/auth/oauth/github/start?mode=sign_in');
  });

  it('carries mode=link for the explicit account-linking flow', () => {
    expect(githubOAuthStartUrl('link')).toBe('/api/v1/auth/oauth/github/start?mode=link');
  });
});

function sessionBody() {
  return { user: VERIFIED_USER, is_new_user: false, provider_linked: false };
}
