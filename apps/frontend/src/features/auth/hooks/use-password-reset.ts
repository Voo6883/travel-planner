'use client';

import { useMutation, type UseMutationResult } from '@tanstack/react-query';
import { resetPassword } from '@/lib/api/auth-api';
import type { ResetPasswordFormValues } from '../types';

export interface UseResetPasswordParams {
  /** The single-use token from the mailed link. */
  token: string;
}

/**
 * Set a new password from a reset link (UC-A07).
 *
 * The token comes from the URL and the passwords from the form, so the hook binds the token once
 * rather than making every caller pass it (PLAN §4.2.6-B2).
 *
 * Two response cases share `400` and mean opposite things (contract `InvalidToken`):
 * `invalid_token` — the link is spent or expired, and the user needs a new one — versus
 * `validation_failed`, where the *password* was rejected and **the token is not consumed**, so
 * the same link still works. The panel keeps the form mounted for the second case precisely
 * because of that.
 */
export function useResetPassword(
  params: UseResetPasswordParams,
): UseMutationResult<void, unknown, ResetPasswordFormValues> {
  return useMutation({
    mutationFn: (values: ResetPasswordFormValues) =>
      resetPassword({ token: params.token, new_password: values.new_password }),
  });
}
