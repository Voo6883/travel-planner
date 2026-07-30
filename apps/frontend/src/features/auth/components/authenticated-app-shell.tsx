'use client';

import type { ReactNode } from 'react';
import { AccountMenu } from '@/components/layout/account-menu';
import { AppShell } from '@/components/layout/app-shell';
import { useCurrentUser } from '../hooks/use-current-user';
import { useSignOut } from '../hooks/use-sign-out';

export interface AuthenticatedAppShellProps {
  children: ReactNode;
  /** Rendered next to the brand as an "Admin" context label (§8.10). */
  contextLabel?: string;
}

/**
 * The app shell, wired to the signed-in user.
 *
 * <b>This file exists to hold one dependency in the right direction.</b> `AppShell` and
 * `AccountMenu` live in `components/layout/` — shared chrome, reusable by anything — and they used to
 * call `useCurrentUser` and `useSignOut` from this feature themselves. That is a shared component
 * importing a feature, which PLAN §4.0.8's vertical slices forbid, and it needed a carve-out in
 * `eslint.config.mjs` to compile at all (recorded as F-25, and named again by the 2026-07-29 review).
 *
 * <b>Why inversion rather than widening the rule.</b> The carve-out was scoped to
 * `src/components/layout/**`, so it was honest about being an exception — but an exception with a
 * plausible justification is how a boundary erodes: the next component that renders identity has a
 * precedent to point at, and the rule ends up covering nothing. Deleting the exception instead means
 * the boundary is enforced everywhere and this file is the one place that knows both sides.
 *
 * The chrome keeps its own tests and can be rendered without a session (the offline page needs
 * exactly that); this composition is what every authenticated route uses.
 */
export function AuthenticatedAppShell({ children, contextLabel }: AuthenticatedAppShellProps) {
  const { data: user } = useCurrentUser();
  const signOut = useSignOut();

  return (
    <AppShell
      contextLabel={contextLabel}
      // §5.1. Absent while the session query is in flight, which renders as "not an admin" for a
      // moment — the correct default: showing the link first and removing it would be a flicker that
      // looks like a permission being revoked.
      isAdmin={user?.roles.includes('ADMIN') ?? false}
      accountMenu={
        <AccountMenu email={user?.email ?? ''} onSignOut={() => signOut.mutate()} signOutPending={signOut.isPending} />
      }
    >
      {children}
    </AppShell>
  );
}
