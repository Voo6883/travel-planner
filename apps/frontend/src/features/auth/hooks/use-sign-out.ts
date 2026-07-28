'use client';

import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useCallback } from 'react';
import { logout, logoutEverywhere } from '@/lib/api/auth-api';
import { SIGN_IN_ROUTE } from './use-auth-session';

/** UC-A10. Idempotent and public — it must succeed even after the access token expired. */
export function useSignOut(): UseMutationResult<void, unknown, void> {
  const finish = useSessionTeardown();

  return useMutation({
    mutationFn: logout,
    // Even a failed logout clears locally. A user who clicked "sign out" on a shared machine and
    // was left signed in because the network blipped is the worst possible outcome here; the
    // server-side revocation is idempotent, so a later retry costs nothing.
    onSettled: finish,
  });
}

/** ADR 009 §5. Bumps `token_version`, so every device — including this one — is signed out. */
export function useSignOutEverywhere(): UseMutationResult<void, unknown, void> {
  const finish = useSessionTeardown();

  return useMutation({
    mutationFn: logoutEverywhere,
    onSuccess: finish,
  });
}

/**
 * Clears every cached byte of the previous user before navigating.
 *
 * `queryClient.clear()` rather than an invalidation: invalidated data stays readable until the
 * refetch lands, which on a shared machine means the next person can see the previous user's
 * screen for a moment.
 */
function useSessionTeardown(): () => void {
  const queryClient = useQueryClient();
  const router = useRouter();

  return useCallback(() => {
    queryClient.clear();
    router.replace(SIGN_IN_ROUTE);
  }, [queryClient, router]);
}
