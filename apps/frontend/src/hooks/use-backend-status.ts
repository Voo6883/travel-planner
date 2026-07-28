'use client';

import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { fetchBackendStatus, type BackendStatus } from '@/lib/api/health-api';
import { queryKeys } from '@/lib/query/query-keys';

/**
 * Backend reachability for the status card, now on React Query (the task 03 file left this as a
 * `useState` + `useEffect` pair with a note that task 11 would move it).
 *
 * `fetchBackendStatus` returns a status instead of throwing, so an unreachable backend is data
 * rather than an error — the shell must render without it. That also means `retry` would be
 * pointless here: there is no failure for React Query to see.
 */
export function useBackendStatus(): UseQueryResult<BackendStatus, unknown> {
  return useQuery({
    queryKey: queryKeys.platform.health(),
    queryFn: ({ signal }) => fetchBackendStatus(signal),
    retry: false,
  });
}
