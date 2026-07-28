import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import enAuth from '@/locales/en/auth.json';
import { mockContract } from '@/test/contract-mock';
import { renderWithProviders } from '@/test/render';
import { AuthGuard } from './auth-guard';

const replace = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace, push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/trips/abc/research',
  useSearchParams: () => new URLSearchParams(),
}));

beforeEach(() => {
  replace.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function user(overrides: Record<string, unknown> = {}) {
  return {
    user_id: '6f9619ff-8b86-d011-b42d-00c04fc964ff',
    email: 'aisyah@example.com',
    username: 'aisyah',
    roles: ['USER'],
    email_verified: true,
    linked_providers: ['LOCAL'],
    ...overrides,
  };
}

describe('AuthGuard', () => {
  it('renders the page for a verified session', async () => {
    mockContract({ '/auth/me': { body: user() } });

    renderWithProviders(
      <AuthGuard>
        <p>planner</p>
      </AuthGuard>,
    );

    expect(await screen.findByText('planner')).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it('sends a signed-out visitor to sign-in, carrying where they were aiming', async () => {
    mockContract({
      '/auth/me': { status: 401, body: { code: 'unauthorized', message: 'Sign in.' } },
    });

    renderWithProviders(
      <AuthGuard>
        <p>planner</p>
      </AuthGuard>,
    );

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith('/sign-in?redirect=%2Ftrips%2Fabc%2Fresearch'),
    );
    // The protected content must never render, not even for a frame.
    expect(screen.queryByText('planner')).not.toBeInTheDocument();
  });

  it('gates an unverified local account instead of bouncing it to sign-in (UC-A08)', async () => {
    // Signing in again would not help — the user has already done that. What they need is the
    // resend action and the address they typed.
    mockContract({ '/auth/me': { body: user({ email_verified: false }) } });

    renderWithProviders(
      <AuthGuard>
        <p>planner</p>
      </AuthGuard>,
    );

    expect(
      await screen.findByRole('heading', { name: enAuth.verification_required_title }),
    ).toBeInTheDocument();
    expect(screen.getByText(/aisyah@example\.com/)).toBeInTheDocument();
    expect(screen.queryByText('planner')).not.toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it('redirects a non-admin away from an admin route without confirming it exists', async () => {
    mockContract({ '/auth/me': { body: user() } });

    renderWithProviders(
      <AuthGuard require="admin">
        <p>admin</p>
      </AuthGuard>,
    );

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/trips'));
    expect(screen.queryByText('admin')).not.toBeInTheDocument();
  });

  it('renders an admin route for an ADMIN role', async () => {
    mockContract({ '/auth/me': { body: user({ roles: ['USER', 'ADMIN'] }) } });

    renderWithProviders(
      <AuthGuard require="admin">
        <p>admin</p>
      </AuthGuard>,
    );

    expect(await screen.findByText('admin')).toBeInTheDocument();
  });

  it('offers a retry rather than a redirect when the session check itself fails', async () => {
    // A 500 is not "signed out". Redirecting on it would sign a live session out over a blip.
    mockContract({
      '/auth/me': { status: 500, body: { code: 'internal_error', message: 'boom' } },
    });

    renderWithProviders(
      <AuthGuard>
        <p>planner</p>
      </AuthGuard>,
    );

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
