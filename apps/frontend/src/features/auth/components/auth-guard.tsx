'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, type ReactNode } from 'react';
import { ErrorAlert } from '@/components/ui/error-alert';
import { LoadingState } from '@/components/ui/loading-state';
import { ApiError } from '@/lib/api/api-error';
import { useCurrentUser } from '../hooks/use-current-user';
import { PLANNER_HOME_ROUTE, REDIRECT_PARAM, SIGN_IN_ROUTE } from '../hooks/use-auth-session';
import { VerificationGate } from './verification-gate';

export interface AuthGuardProps {
  children: ReactNode;
  /** `admin` additionally requires the `ADMIN` role (§8.10). */
  require?: 'authenticated' | 'admin';
}

/**
 * The session gate for `(planner)` and `(admin)`.
 *
 * **The guard is a UX affordance, not the security boundary.** Authorisation is enforced by
 * Spring Security on every request; nothing here can be trusted by the server, and nothing here
 * needs to be. What it buys is that a signed-out user sees the sign-in page instead of a screen
 * full of failed requests.
 *
 * It asks the server rather than reading local state because there is no local state to read:
 * `tp_session` is `httpOnly`. `GET /auth/me` is the only way to learn whether the session is
 * live, and because it is answered from current database state (ADR 009 §2) a revoked session is
 * caught on the next check rather than at token expiry — which is what makes it survive a refresh
 * and a background tab.
 *
 * The unverified-email case is a gate, not a redirect: UC-A08 blocks the planner until the
 * address is confirmed, and the user needs the resend action, not a bounce to sign-in they have
 * already completed.
 */
export function AuthGuard({ children, require = 'authenticated' }: AuthGuardProps) {
  const t = useTranslations('common');
  // Not destructured — React Query's result is a discriminated union, and destructuring loses the
  // narrowing that proves `query.data` exists once the pending and error branches are past.
  const query = useCurrentUser();
  const isSignedOut = query.error instanceof ApiError && query.error.status === 401;
  const isForbidden =
    require === 'admin' && query.data !== undefined && !query.data.roles.includes('ADMIN');

  useRedirectWhen(isSignedOut, SIGN_IN_ROUTE);
  useRedirectWhen(isForbidden, PLANNER_HOME_ROUTE);

  if (query.isPending || isSignedOut || isForbidden) {
    return (
      <GuardFrame>
        <LoadingState label={t('states.checking_session')} />
      </GuardFrame>
    );
  }

  if (query.isError) {
    return (
      <GuardFrame>
        <ErrorAlert error={query.error} onRetry={() => void query.refetch()} />
      </GuardFrame>
    );
  }

  if (!query.data.email_verified) {
    return <VerificationGate email={query.data.email} />;
  }

  return <>{children}</>;
}

/** The §4.2 page padding, so a guard state is not flush against the viewport edge. */
function GuardFrame({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-6 md:px-6 md:py-8 xl:px-8">{children}</div>
  );
}

/**
 * Navigates once the condition holds, carrying the current path so sign-in can return the user
 * where they were aiming. `replace`, not `push`: a guarded page the user cannot see should not
 * become a Back-button destination.
 */
function useRedirectWhen(active: boolean, target: string): void {
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!active) {
      return;
    }
    const query =
      target === SIGN_IN_ROUTE ? `?${REDIRECT_PARAM}=${encodeURIComponent(pathname)}` : '';
    router.replace(`${target}${query}`);
  }, [active, target, pathname, router]);
}
