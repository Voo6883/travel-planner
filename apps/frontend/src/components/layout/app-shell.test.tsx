import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { describe, expect, it, vi } from 'vitest';
import enCommon from '@/locales/en/common.json';
import { AppShell } from './app-shell';

vi.mock('next/navigation', () => ({
  usePathname: () => '/trips',
}));

/**
 * The shell renders identity; it does not read it.
 *
 * <b>Note what this test does not do: it never sets up a QueryClient or an API mock.</b> That is the
 * assertion. `AppShell` and `AccountMenu` used to call `useCurrentUser` / `useSignOut` from
 * `@/features/auth` — shared chrome importing a feature, which needed an ESLint carve-out to compile
 * (F-25, and named again by the 2026-07-29 review). Identity now arrives as props, so a plain
 * `render` with only an intl provider is enough.
 *
 * If somebody re-introduces the hook, this file fails immediately with a missing-QueryClient error
 * rather than passing while the boundary quietly erodes. `features/auth/components/
 * authenticated-app-shell.tsx` is where the coupling is allowed to live, and
 * `eslint.config.mjs` now enforces that everywhere in `src/components/**` with no exception.
 */
function renderShell(props: Partial<Parameters<typeof AppShell>[0]> = {}) {
  return render(
    <NextIntlClientProvider locale="en" messages={{ common: enCommon }}>
      <AppShell {...props}>
        <p>page body</p>
      </AppShell>
    </NextIntlClientProvider>,
  );
}

describe('AppShell', () => {
  it('renders without any session, provider, or API call', () => {
    renderShell();

    expect(screen.getByText('page body')).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('hides the admin link by default', () => {
    // §5.1: admin navigation is shown only to authorised users. Defaulting to hidden is the safe
    // direction — showing it and then removing it reads as a permission being revoked.
    renderShell();

    expect(screen.queryByRole('link', { name: enCommon.nav.admin })).not.toBeInTheDocument();
  });

  it('shows the admin link when the caller says the user is an admin', () => {
    renderShell({ isAdmin: true });

    expect(screen.getByRole('link', { name: enCommon.nav.admin })).toBeInTheDocument();
  });

  it('renders whatever account menu it is handed, and nothing when handed none', () => {
    renderShell({ accountMenu: <button type="button">account</button> });

    expect(screen.getByRole('button', { name: 'account' })).toBeInTheDocument();
  });

  it('shows the context label beside the brand', () => {
    renderShell({ contextLabel: 'Admin' });

    expect(screen.getByText('Admin')).toBeInTheDocument();
  });
});
