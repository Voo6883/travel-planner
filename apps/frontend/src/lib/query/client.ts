import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/lib/api/api-error';

/** PLAN §4.2.6-G: 60 s default freshness; polling screens opt down to 0 per query. */
export const DEFAULT_STALE_TIME_MS = 60_000;

/** Two retries on a network fault, none on a 4xx. */
export const MAX_QUERY_RETRIES = 2;

/**
 * Retrying a 4xx is a bug, not resilience: `401`, `403`, `404`, and `validation_failed` are
 * verdicts about the request, so a retry produces the same answer while delaying the error the
 * user needs to see. `429` is excluded too — it carries `retry_after_seconds`, and the UI shows a
 * countdown rather than hammering the endpoint.
 *
 * A `5xx` or an offline `TypeError` from `fetch` is genuinely worth retrying, and those are what
 * fall through to the retry count.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    return false;
  }
  return failureCount < MAX_QUERY_RETRIES;
}

/**
 * One client per browser session, created lazily by the provider.
 *
 * Mutations never retry. A retried `POST /auth/register` or `PUT /auth/password` is a second
 * write, and the client cannot know whether the first one landed before the connection dropped.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: DEFAULT_STALE_TIME_MS,
        retry: shouldRetry,
        // The session cookie can expire while a tab sits in the background; re-checking on focus
        // is how a guarded screen notices it is no longer authenticated.
        refetchOnWindowFocus: true,
        refetchOnReconnect: true,
      },
      mutations: {
        retry: false,
      },
    },
  });
}
