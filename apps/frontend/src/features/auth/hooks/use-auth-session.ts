'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useRouter, useSearchParams } from 'next/navigation';
import { useCallback } from 'react';
import type { AuthSessionResponse } from '@/lib/api/auth-api';
import { queryKeys } from '@/lib/query/query-keys';

/** Where an unauthenticated visitor is sent, and where they come back to. */
export const SIGN_IN_ROUTE = '/sign-in';
export const PLANNER_HOME_ROUTE = '/trips';
export const REDIRECT_PARAM = 'redirect';

export interface EstablishedSession {
  establish: (session: AuthSessionResponse) => void;
}

/**
 * The tail end of every successful sign-in, in one place (PLAN §4.2.6-B4).
 *
 * Three things have to happen together, and doing any of them alone produces a visible bug:
 *
 * 1. **Seed the cache** with the user the sign-in response already contains, so the planner does
 *    not flash its "checking your session" skeleton immediately after a successful sign-in.
 * 2. **Invalidate** the rest of the auth subtree — anything derived from "who am I" is stale the
 *    moment the answer changes.
 * 3. **Replace, not push.** `router.replace` keeps the sign-in page out of history, so Back from
 *    the planner does not land on a form the user has already completed.
 *
 * `?redirect=` is honoured only when it is a same-origin path. An absolute URL there would turn
 * the sign-in page into an open redirect that a phishing mail could point anywhere.
 */
export function useEstablishSession(): EstablishedSession {
  const router = useRouter();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();

  const establish = useCallback(
    (session: AuthSessionResponse) => {
      queryClient.setQueryData(queryKeys.auth.currentUser(), session.user);
      void queryClient.invalidateQueries({ queryKey: queryKeys.auth.all });
      router.replace(safeRedirect(searchParams.get(REDIRECT_PARAM)));
    },
    [queryClient, router, searchParams],
  );

  return { establish };
}

/**
 * Accepts only a path on this origin. `//evil.example` is rejected too: browsers read a
 * protocol-relative URL as an absolute one, and it is the form an open-redirect check most often
 * forgets.
 */
export function safeRedirect(target: string | null): string {
  if (!target || !target.startsWith('/') || target.startsWith('//')) {
    return PLANNER_HOME_ROUTE;
  }
  return target;
}
