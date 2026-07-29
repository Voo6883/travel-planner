'use client';

import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useCallback } from 'react';
import { changePassword, deleteCurrentUser, unlinkProvider, type IdentityProvider } from '@/lib/api/auth-api';
import { SIGN_IN_ROUTE } from './use-auth-session';
import type { ChangePasswordFormValues } from '../types';

/**
 * The three settings actions that end the caller's own session (UC-A12, UC-A14, ADR 009 §1/§4).
 *
 * They are grouped because they share one non-obvious consequence: each one revokes every
 * session, *including this tab's*. A UI that left the user on the settings page afterwards would
 * show them a screen whose next request is guaranteed to 401. So each one tears the cache down
 * and returns to sign-in — that is the success path, not an error path.
 */

export function useChangePassword(): UseMutationResult<void, unknown, ChangePasswordFormValues> {
  const finish = useSessionTeardown();

  return useMutation({
    mutationFn: (values: ChangePasswordFormValues) =>
      changePassword({
        current_password: values.current_password,
        new_password: values.new_password,
      }),
    onSuccess: finish,
  });
}

export function useUnlinkProvider(): UseMutationResult<void, unknown, IdentityProvider> {
  const finish = useSessionTeardown();

  return useMutation({
    mutationFn: (provider: IdentityProvider) => unlinkProvider(provider),
    onSuccess: finish,
  });
}

export function useDeleteAccount(): UseMutationResult<void, unknown, void> {
  const finish = useSessionTeardown();

  return useMutation({
    mutationFn: deleteCurrentUser,
    onSuccess: finish,
  });
}

function useSessionTeardown(): () => void {
  const queryClient = useQueryClient();
  const router = useRouter();

  return useCallback(() => {
    queryClient.clear();
    router.replace(SIGN_IN_ROUTE);
  }, [queryClient, router]);
}
