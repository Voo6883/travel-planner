import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import enAuth from '@/locales/en/auth.json';
import enCommon from '@/locales/en/common.json';
import msAuth from '@/locales/ms/auth.json';
import { mockContract } from '@/test/contract-mock';
import { renderWithProviders } from '@/test/render';
import { SignInPanel } from './sign-in-panel';

const replace = vi.fn();
let searchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace, push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => searchParams,
  usePathname: () => '/sign-in',
}));

beforeEach(() => {
  searchParams = new URLSearchParams();
  replace.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const VERIFIED_SESSION = {
  user: {
    user_id: '6f9619ff-8b86-d011-b42d-00c04fc964ff',
    email: 'aisyah@example.com',
    username: 'aisyah',
    roles: ['USER'],
    email_verified: true,
    linked_providers: ['LOCAL'],
  },
  is_new_user: false,
  provider_linked: false,
};

describe('SignInPanel', () => {
  it('offers both providers and local credentials, in that order (§8.2)', () => {
    renderWithProviders(<SignInPanel />);

    expect(screen.getByRole('button', { name: enAuth.continue_with_google })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: enAuth.continue_with_github })).toBeInTheDocument();
    expect(screen.getByLabelText(enAuth.login_label)).toBeInTheDocument();
  });

  it('accepts a username on the same field as an email and posts it verbatim', async () => {
    // The server disambiguates on `@`; a client-side guess would reject a correct password
    // whenever it disagreed.
    const contract = mockContract({ '/auth/login': { body: VERIFIED_SESSION } });
    const user = userEvent.setup();

    renderWithProviders(<SignInPanel />);
    await user.type(screen.getByLabelText(enAuth.login_label), 'aisyah');
    await user.type(screen.getByLabelText(enAuth.password_label), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: enAuth.sign_in_submit }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/trips'));
    const call = contract.lastCall();
    expect(call.url).toBe('/api/v1/auth/login');
    expect(JSON.parse(call.body ?? '{}')).toEqual({
      login: 'aisyah',
      password: 'correct-horse-battery',
    });
  });

  it('validates locally before spending a request', async () => {
    const contract = mockContract({});
    const user = userEvent.setup();

    renderWithProviders(<SignInPanel />);
    await user.click(screen.getByRole('button', { name: enAuth.sign_in_submit }));

    expect(await screen.findByText(enAuth.validation_login_required)).toBeInTheDocument();
    expect(contract.calls).toHaveLength(0);
  });

  it('shows the translated message for a rejected credential, never the developer string', async () => {
    mockContract({
      '/auth/login': {
        status: 401,
        body: { code: 'invalid_credentials', message: 'The email or password is incorrect.' },
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<SignInPanel />);
    await user.type(screen.getByLabelText(enAuth.login_label), 'aisyah');
    await user.type(screen.getByLabelText(enAuth.password_label), 'wrong');
    await user.click(screen.getByRole('button', { name: enAuth.sign_in_submit }));

    expect(await screen.findByText(enCommon.errors.invalid_credentials)).toBeInTheDocument();
    // The envelope's English `message` is a developer string and must never reach a user.
    expect(screen.queryByText('The email or password is incorrect.')).not.toBeInTheDocument();
  });

  it('offers the resend flow when the password was right but the address is unconfirmed', async () => {
    // `email_not_verified` is distinct from `invalid_credentials` precisely so this is possible.
    mockContract({
      '/auth/login': {
        status: 403,
        body: { code: 'email_not_verified', message: 'Not verified.' },
      },
    });
    const user = userEvent.setup();

    renderWithProviders(<SignInPanel />);
    await user.type(screen.getByLabelText(enAuth.login_label), 'aisyah');
    await user.type(screen.getByLabelText(enAuth.password_label), 'correct-horse-battery');
    await user.click(screen.getByRole('button', { name: enAuth.sign_in_submit }));

    const resend = await screen.findByRole('link', { name: enAuth.resend_verification_submit });
    expect(resend).toHaveAttribute('href', '/verify-email');
  });

  it('renders a GitHub redirect failure from ?error= through the same translated path', async () => {
    searchParams = new URLSearchParams('error=invalid_oauth_state');
    mockContract({});

    renderWithProviders(<SignInPanel />);

    expect(await screen.findByText(enCommon.errors.invalid_oauth_state)).toBeInTheDocument();
  });

  it('ignores an unregistered code in the query string', async () => {
    // The query string is attacker-controllable; rendering it verbatim would let a crafted link
    // put arbitrary text on the sign-in page.
    searchParams = new URLSearchParams('error=<script>alert(1)</script>');
    mockContract({});

    renderWithProviders(<SignInPanel />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders Malay copy end to end', () => {
    renderWithProviders(<SignInPanel />, { locale: 'ms' });

    expect(screen.getByRole('heading', { name: msAuth.sign_in_title })).toBeInTheDocument();
    expect(screen.getByLabelText(msAuth.login_label)).toBeInTheDocument();
  });
});
