'use client';

import { useMutation, type UseMutationResult } from '@tanstack/react-query';
import { login, type AuthSessionResponse, type LoginRequest } from '@/lib/api/auth-api';
import { useEstablishSession } from './use-auth-session';
import type { SignInFormValues } from '../types';

/**
 * Local sign-in (UC-A04).
 *
 * The form values map to the request here rather than in the component (PLAN §4.2.6-F): the one
 * field carries an email *or* a username, and the server disambiguates on `@`. The client
 * deliberately does not guess — a client-side guess that disagreed with the server would produce
 * "invalid credentials" for a correct password.
 *
 * Failures are left to the caller. `invalid_credentials`, `email_not_verified`, `account_locked`,
 * and `account_disabled` all need different UI, and swallowing them here would flatten that into
 * one message.
 */
export function useSignIn(): UseMutationResult<AuthSessionResponse, unknown, SignInFormValues> {
  const { establish } = useEstablishSession();

  return useMutation({
    mutationFn: (values: SignInFormValues) => login(toLoginRequest(values)),
    onSuccess: establish,
  });
}

function toLoginRequest(values: SignInFormValues): LoginRequest {
  return { login: values.login.trim(), password: values.password };
}
