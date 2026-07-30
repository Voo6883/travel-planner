import { getTranslations } from 'next-intl/server';
import type { ReactNode } from 'react';
import { AuthGuard, AuthenticatedAppShell } from '@/features/auth';

/**
 * `(admin)` requires the `ADMIN` role on top of a live session (§8.10).
 *
 * A non-admin is redirected to a safe planner route and shown no admin navigation — §8.10's
 * wording, and the reason the guard redirects rather than rendering a "forbidden" page: telling
 * someone an admin area exists at this exact path is information they did not have.
 *
 * The shell is the shared one, with a visible "Admin" context label. §8.10 is explicit that admin
 * shares all tokens and components with the planner and gets no separate theme.
 *
 * Admin *screens* are task 12; this task owns the guard and the labelled shell they land in.
 */
export default async function AdminLayout({ children }: { children: ReactNode }) {
  const t = await getTranslations('common');

  return (
    <AuthGuard require="admin">
      <AuthenticatedAppShell contextLabel={t('nav.admin_context')}>{children}</AuthenticatedAppShell>
    </AuthGuard>
  );
}
