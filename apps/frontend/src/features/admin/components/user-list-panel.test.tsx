import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import enAdmin from '@/locales/en/admin.json';
import { mockContract } from '@/test/contract-mock';
import { renderWithProviders } from '@/test/render';
import { UserListPanel } from './user-list-panel';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/admin/users',
  useSearchParams: () => new URLSearchParams(),
}));

afterEach(() => {
  vi.unstubAllGlobals();
});

function summary(overrides: Record<string, unknown> = {}) {
  return {
    user_id: '6f9619ff-8b86-d011-b42d-00c04fc964ff',
    email: 'aisyah@example.com',
    username: 'aisyah',
    roles: ['USER'],
    email_verified: true,
    enabled: true,
    closed: false,
    created_at: '2026-07-01T09:30:00Z',
    ...overrides,
  };
}

function page(items: Record<string, unknown>[], total = items.length) {
  return { page: 0, page_size: 20, total, items };
}

describe('UserListPanel', () => {
  it('renders one row per account with its status spelled out', async () => {
    mockContract({
      '/admin/users': {
        body: page([
          summary(),
          summary({
            user_id: '11111111-1111-4111-8111-111111111111',
            email: 'disabled@example.com',
            enabled: false,
          }),
        ]),
      },
    });

    renderWithProviders(<UserListPanel />);

    expect(await screen.findByText('aisyah@example.com')).toBeInTheDocument();
    expect(screen.getByText('disabled@example.com')).toBeInTheDocument();
    // §10.1 — colour never carries the meaning alone.
    expect(screen.getByText(enAdmin.status.enabled)).toBeInTheDocument();
    expect(screen.getByText(enAdmin.status.disabled)).toBeInTheDocument();
  });

  it('distinguishes an owner-closed account from one an administrator disabled', async () => {
    // Both have enabled=false. Showing the closed one as merely "disabled" would invite an
    // administrator to switch it back on, which the server refuses with 409 account_closed.
    mockContract({
      '/admin/users': { body: page([summary({ enabled: false, closed: true })]) },
    });

    renderWithProviders(<UserListPanel />);

    expect(await screen.findByText(enAdmin.status.closed)).toBeInTheDocument();
    expect(screen.queryByText(enAdmin.status.disabled)).not.toBeInTheDocument();
  });

  it('requests the first page zero-based, as the contract publishes it', async () => {
    const mock = mockContract({ '/admin/users': { body: page([summary()]) } });

    renderWithProviders(<UserListPanel />);
    await screen.findByText('aisyah@example.com');

    // Ant Design's pagination is one-based; the contract's `page` is not. Sending 1 for the first
    // page would silently skip an entire page of accounts.
    expect(mock.lastCall().url).toContain('page=0');
    expect(mock.lastCall().credentials).toBe('include');
  });

  it('shows an empty state rather than an error when there are genuinely no accounts', async () => {
    mockContract({ '/admin/users': { body: page([], 0) } });

    renderWithProviders(<UserListPanel />);

    expect(await screen.findByText(enAdmin.list.empty_title)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('offers a retry rather than an empty table when the request fails', async () => {
    mockContract({
      '/admin/users': { status: 500, body: { code: 'internal_error', message: 'boom' } },
    });

    renderWithProviders(<UserListPanel />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText(enAdmin.list.empty_title)).not.toBeInTheDocument();
  });

  it('translates the whole screen into Malay', async () => {
    // §11.3: every visible string comes from next-intl. A hard-coded English label survives an
    // `en` test and fails here.
    mockContract({ '/admin/users': { body: page([summary()]) } });

    renderWithProviders(<UserListPanel />, { locale: 'ms' });

    expect(await screen.findByText('aisyah@example.com')).toBeInTheDocument();
    expect(screen.queryByText(enAdmin.status.enabled)).not.toBeInTheDocument();
  });
});
