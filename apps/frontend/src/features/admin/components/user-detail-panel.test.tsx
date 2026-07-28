import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import enAdmin from '@/locales/en/admin.json';
import enCommon from '@/locales/en/common.json';
import { mockContract } from '@/test/contract-mock';
import { renderWithProviders } from '@/test/render';
import { UserDetailPanel } from './user-detail-panel';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/admin/users/6f9619ff-8b86-d011-b42d-00c04fc964ff',
  useSearchParams: () => new URLSearchParams(),
}));

afterEach(() => {
  vi.unstubAllGlobals();
});

const USER_ID = '6f9619ff-8b86-d011-b42d-00c04fc964ff';

function detail(overrides: Record<string, unknown> = {}) {
  return {
    user_id: USER_ID,
    email: 'aisyah@example.com',
    username: 'aisyah',
    roles: ['USER'],
    email_verified: true,
    enabled: true,
    closed: false,
    created_at: '2026-07-01T09:30:00Z',
    linked_providers: ['LOCAL'],
    has_local_password: true,
    updated_at: '2026-07-02T09:30:00Z',
    ...overrides,
  };
}

describe('UserDetailPanel', () => {
  it('shows account state and never anything belonging to the account owner', async () => {
    mockContract({ '/admin/users/{userId}': { body: detail() } });

    renderWithProviders(<UserDetailPanel userId={USER_ID} />);

    expect(await screen.findByText('aisyah@example.com')).toBeInTheDocument();
    expect(screen.getByText(enAdmin.status.enabled)).toBeInTheDocument();
    // The whole point of the admin scope boundary: nothing here reaches a user's trips.
    expect(screen.queryByText(/trip/i)).not.toBeInTheDocument();
  });

  it('requires an explicit confirmation before disabling, and says sessions will end', async () => {
    const mock = mockContract({
      '/admin/users/{userId}': { body: detail() },
    });
    const user = userEvent.setup();

    renderWithProviders(<UserDetailPanel userId={USER_ID} />);
    await user.click(await screen.findByRole('button', { name: enAdmin.status_action.disable }));

    // §6.6 — a destructive confirmation names the affected item and states the consequence.
    expect(
      await screen.findByText(
        enAdmin.status_action.disable_confirm_title.replace('{email}', 'aisyah@example.com'),
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(enAdmin.status_action.disable_confirm_body)).toBeInTheDocument();
    // Nothing was sent by opening the dialog — only the initial GET.
    expect(mock.calls.filter((call) => call.method === 'PUT')).toHaveLength(0);
  });

  it('sends the disable only after the confirmation is accepted', async () => {
    const mock = mockContract({
      '/admin/users/{userId}': { body: detail() },
    });
    const user = userEvent.setup();

    renderWithProviders(<UserDetailPanel userId={USER_ID} />);
    await user.click(await screen.findByRole('button', { name: enAdmin.status_action.disable }));
    const dialog = await screen.findByRole('dialog');
    await user.click(
      within(dialog).getByRole('button', { name: enAdmin.status_action.disable }),
    );

    await waitFor(() => {
      const put = mock.calls.find((call) => call.method === 'PUT');
      expect(put?.body).toBe(JSON.stringify({ enabled: false }));
    });
  });

  it('surfaces a refusal instead of pretending the account changed', async () => {
    // The self-disable guard and the closed-account conflict both arrive this way. A UI that
    // flipped the row optimistically would show a state the server never accepted.
    mockContract({
      '/admin/users/{userId}': { body: detail() },
    });
    const user = userEvent.setup();

    renderWithProviders(<UserDetailPanel userId={USER_ID} />);
    await user.click(await screen.findByRole('button', { name: enAdmin.status_action.disable }));

    mockContract({
      '/admin/users/{userId}': {
        status: 403,
        body: { code: 'forbidden', message: 'not allowed' },
      },
    });
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: enAdmin.status_action.disable }));

    expect(await screen.findByText(enCommon.errors.forbidden)).toBeInTheDocument();
  });

  it('refuses to offer a password reset for a provider-only account', async () => {
    // ADR 009 §4 — there is no local password to replace, and the server answers
    // validation_failed. Disabling the control avoids inviting a click that cannot succeed.
    mockContract({
      '/admin/users/{userId}': {
        body: detail({ has_local_password: false, linked_providers: ['FIREBASE_GOOGLE'] }),
      },
    });

    renderWithProviders(<UserDetailPanel userId={USER_ID} />);

    expect(await screen.findByRole('button', { name: enAdmin.reset.open_action })).toBeDisabled();
    expect(screen.getByText(enAdmin.reset.unavailable_provider_only)).toBeInTheDocument();
  });

  it('locks both actions and explains why for an account its owner closed', async () => {
    mockContract({
      '/admin/users/{userId}': {
        body: detail({ enabled: false, closed: true, has_local_password: false, username: null }),
      },
    });

    renderWithProviders(<UserDetailPanel userId={USER_ID} />);

    expect(await screen.findByText(enAdmin.detail.closed_notice)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: enAdmin.status_action.enable })).toBeDisabled();
    expect(screen.getByRole('button', { name: enAdmin.reset.open_action })).toBeDisabled();
  });

  it('warns before a reset that the password cannot be read back and ends every session',
    async () => {
      mockContract({ '/admin/users/{userId}': { body: detail() } });
      const user = userEvent.setup();

      renderWithProviders(<UserDetailPanel userId={USER_ID} />);
      await user.click(await screen.findByRole('button', { name: enAdmin.reset.open_action }));

      expect(await screen.findByText(enAdmin.reset.warning)).toBeInTheDocument();
      expect(
        screen.getByText(enAdmin.reset.title.replace('{email}', 'aisyah@example.com')),
      ).toBeInTheDocument();
    });

  it('offers a retry rather than a blank screen when the account cannot be loaded', async () => {
    mockContract({
      '/admin/users/{userId}': {
        status: 404,
        body: { code: 'user_not_found', message: 'no such account' },
      },
    });

    renderWithProviders(<UserDetailPanel userId={USER_ID} />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(enCommon.errors.user_not_found)).toBeInTheDocument();
  });
});
