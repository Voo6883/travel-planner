'use client';

import { useMutation, type UseMutationResult } from '@tanstack/react-query';
import { authenticateWithFirebase, type AuthSessionResponse } from '@/lib/api/auth-api';
import {
  FIREBASE_POPUP_BLOCKED,
  FIREBASE_POPUP_CANCELLED,
  requestGoogleIdToken,
} from '../lib/firebase-client';
import { useEstablishSession } from './use-auth-session';

/** Raised when the user closed the Google window — a decision, not a failure to report. */
export class GooglePopupCancelled extends Error {}

/** Raised when the browser blocked the pop-up; the recovery is to allow pop-ups, not to retry. */
export class GooglePopupBlocked extends Error {}

/**
 * Google sign-up **and** sign-in through Firebase (UC-A02, UC-A05).
 *
 * One hook and one endpoint for both, exactly as PLAN §4.0.5 locks: the browser obtains an ID
 * token from `signInWithPopup`, posts it, and the server decides whether that means "created",
 * "signed in", or "linked". `is_new_user` and `provider_linked` in the response are what the UI
 * reads afterwards — the client never asserts which case it is, because a client claim about
 * account state is a claim the caller could simply make up.
 *
 * Pop-up outcomes are translated into typed errors rather than surfaced raw. A closed window is
 * not an error a user should see an alert for, and a blocked pop-up needs a different instruction
 * from "try again".
 */
export function useGoogleAuth(): UseMutationResult<AuthSessionResponse, unknown, void> {
  const { establish } = useEstablishSession();

  return useMutation({
    mutationFn: async () => {
      const idToken = await requestGoogleIdToken().catch(rethrowPopupFailure);
      return authenticateWithFirebase({ id_token: idToken });
    },
    onSuccess: establish,
  });
}

function rethrowPopupFailure(error: unknown): never {
  const code = readFirebaseErrorCode(error);
  if (code !== null && FIREBASE_POPUP_CANCELLED.has(code)) {
    throw new GooglePopupCancelled(code);
  }
  if (code === FIREBASE_POPUP_BLOCKED) {
    throw new GooglePopupBlocked(code);
  }
  throw error;
}

function readFirebaseErrorCode(error: unknown): string | null {
  if (typeof error !== 'object' || error === null || !('code' in error)) {
    return null;
  }
  const code = (error as { code: unknown }).code;
  return typeof code === 'string' ? code : null;
}
