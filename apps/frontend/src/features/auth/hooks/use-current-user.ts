'use client';

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { fetchCurrentUser, type CurrentUser } from '@/lib/api/auth-api';
import { queryKeys } from '@/lib/query/query-keys';

/**
 * The session, read from the server (UC-A11).
 *
 * There is no client-side session state to consult: `tp_session` is `httpOnly`, so JavaScript
 * cannot see whether it exists or whether it is still valid. `GET /auth/me` *is* the check, and
 * because the backend assembles it from current database state (ADR 009 §2) a revoked session or
 * a just-verified email is reflected immediately rather than at the next token expiry.
 *
 * A `401` is an ordinary outcome here, not a fault — it means "signed out". `shouldRetry` in
 * `lib/query/client.ts` already refuses to retry 4xx, so an anonymous visitor costs one request.
 */
export function useCurrentUser(): UseQueryResult<CurrentUser, unknown> {
  return useQuery({
    queryKey: queryKeys.auth.currentUser(),
    queryFn: ({ signal }) => fetchCurrentUser(signal),
  });
}
