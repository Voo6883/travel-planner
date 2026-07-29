'use client';

import { useMutation, type UseMutationResult } from '@tanstack/react-query';
import { requestPasswordReset, resendEmailVerification, type AcceptedResponse } from '@/lib/api/auth-api';
import type { EmailFormValues } from '../types';

/**
 * The two "send me a mail about this address" flows (UC-A07, UC-A13).
 *
 * They share a hook because they must stay indistinguishable to an observer: one request shape,
 * one constant `202 ACCEPTED`, one rate limit keyed on the submitted string rather than on an
 * account (ADR 009 §6). Writing them separately is how a well-meaning change gives one of them a
 * different response for an unregistered address and reopens the enumeration oracle.
 *
 * `429 rate_limited` is the only failure a caller ever sees, and it carries
 * `retry_after_seconds` — reachable for an unregistered address exactly as for a registered one,
 * so hitting it still reveals nothing.
 */
export function useForgotPassword(): UseMutationResult<AcceptedResponse, unknown, EmailFormValues> {
  return useMutation({
    mutationFn: (values: EmailFormValues) => requestPasswordReset({ email: values.email.trim() }),
  });
}

export function useResendVerification(): UseMutationResult<AcceptedResponse, unknown, EmailFormValues> {
  return useMutation({
    mutationFn: (values: EmailFormValues) => resendEmailVerification({ email: values.email.trim() }),
  });
}
