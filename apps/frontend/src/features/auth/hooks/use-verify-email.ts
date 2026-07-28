'use client';

import { useMutation, type UseMutationResult } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { confirmEmailVerification } from '@/lib/api/auth-api';

export interface UseVerifyEmailParams {
  /** Token from `?token=` on the mailed link, or null when the URL is incomplete. */
  token: string | null;
}

/**
 * Confirms an address from the mailed link (UC-A08).
 *
 * Runs on mount rather than behind a button: the user already expressed intent by clicking the
 * link in their mail, and asking them to click again is a second chance to abandon the flow.
 *
 * The `hasRun` ref is what makes that safe. React 18 Strict Mode mounts effects twice in
 * development, and the token is single-use — without the guard the second call would consume a
 * token that had just succeeded and show the user a failure for a verification that worked.
 */
export function useVerifyEmail(params: UseVerifyEmailParams): UseMutationResult<void, unknown, string> {
  const mutation = useMutation({
    mutationFn: (token: string) => confirmEmailVerification({ token }),
  });
  const hasRun = useRef(false);
  const { mutate } = mutation;
  const { token } = params;

  useEffect(() => {
    if (token === null || hasRun.current) {
      return;
    }
    hasRun.current = true;
    mutate(token);
  }, [token, mutate]);

  return mutation;
}
