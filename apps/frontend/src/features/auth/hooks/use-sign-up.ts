'use client';

import { useMutation, type UseMutationResult } from '@tanstack/react-query';
import { register, type RegisterRequest, type RegistrationResponse } from '@/lib/api/auth-api';
import type { SignUpFormValues } from '../types';

/**
 * Local sign-up (UC-A01).
 *
 * **No session is established and no redirect happens**, and that is not an omission. The
 * response is a constant `202 PENDING_VERIFICATION` whether the account was created, the address
 * was already registered, or the username was taken (ADR 009 §6) — so the UI has nothing to
 * branch on and must not pretend otherwise. It shows one "check your email" state in every case;
 * the real outcome reaches the address owner by mail, which is the only channel they control.
 *
 * Anything that made the success screen differ — a name, a countdown keyed on the account, a
 * "welcome back" — would reopen the enumeration oracle the endpoint closes.
 */
export function useSignUp(): UseMutationResult<RegistrationResponse, unknown, SignUpFormValues> {
  return useMutation({
    mutationFn: (values: SignUpFormValues) => register(toRegisterRequest(values)),
  });
}

function toRegisterRequest(values: SignUpFormValues): RegisterRequest {
  return {
    email: values.email.trim(),
    username: values.username.trim(),
    password: values.password,
  };
}
