import { Suspense, type ReactNode } from 'react';
import { AuthGuard, AuthenticatedAppShell, ProviderLinkedNotice } from '@/features/auth';

/**
 * Everything under `(planner)` requires a live, email-verified session (UC-A08).
 *
 * The guard sits in the layout rather than in each page so a new planner screen is protected by
 * where it lives, not by remembering to wrap it. It is a UX affordance — Spring Security is the
 * actual boundary — but it is the difference between a signed-out user seeing the sign-in page
 * and seeing a dashboard full of failed requests.
 *
 * `ProviderLinkedNotice` is here because the GitHub link round trip lands on `/trips?linked=1`,
 * and the confirmation has to be shown wherever the redirect happens to land.
 */
export default function PlannerLayout({ children }: { children: ReactNode }) {
  return (
    <AuthGuard>
      <Suspense>
        <ProviderLinkedNotice />
      </Suspense>
      <AuthenticatedAppShell>{children}</AuthenticatedAppShell>
    </AuthGuard>
  );
}
