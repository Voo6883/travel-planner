import { describe, expect, it } from 'vitest';
import { ApiError } from '@/lib/api/api-error';
import { createQueryClient, DEFAULT_STALE_TIME_MS, MAX_QUERY_RETRIES, shouldRetry } from './client';

/**
 * Retry policy is a correctness rule, not tuning (PLAN §4.2.6-G).
 *
 * Retrying a 4xx delays the error the user has to act on and, for `429`/`423`, actively makes the
 * situation worse — the endpoint is telling the client to wait, and a retry loop is the client
 * arguing with it.
 */
describe('shouldRetry', () => {
  it.each([400, 401, 403, 404, 409, 423, 429])('never retries a %i', (status) => {
    const error = new ApiError({ status, code: 'unauthorized', message: 'no' });

    expect(shouldRetry(0, error)).toBe(false);
  });

  it('retries a server fault up to the limit', () => {
    const error = new ApiError({ status: 503, code: 'internal_error', message: 'down' });

    expect(shouldRetry(0, error)).toBe(true);
    expect(shouldRetry(MAX_QUERY_RETRIES - 1, error)).toBe(true);
    expect(shouldRetry(MAX_QUERY_RETRIES, error)).toBe(false);
  });

  it('retries a network failure, which never reaches the server at all', () => {
    // `fetch` throws a bare TypeError when the connection drops — no status to inspect.
    expect(shouldRetry(0, new TypeError('Failed to fetch'))).toBe(true);
  });
});

describe('createQueryClient', () => {
  it('applies the planned defaults and never retries a mutation', () => {
    const defaults = createQueryClient().getDefaultOptions();

    expect(defaults.queries?.staleTime).toBe(DEFAULT_STALE_TIME_MS);
    // A retried POST is a second write, and the client cannot know whether the first one landed.
    expect(defaults.mutations?.retry).toBe(false);
  });
});
