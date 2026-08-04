'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import { fetchResearchJob, startResearch, type ResearchJob, type StartResearchCommand } from '@/lib/api/research-api';
import { fetchTrip, type Trip } from '@/lib/api/trip-api';
import { queryKeys } from '@/lib/query/query-keys';

/**
 * C2 research-job server state (UC-C2-01/02).
 *
 * Starting a run seeds the job cache and invalidates the trip detail, whose status the start moved.
 * Polling refetches while the job is `queued` or `running` and stops the moment it is terminal — the
 * React Query `refetchInterval` reads the latest job to decide, so no timer outlives the run.
 *
 * The panel reads trip status through {@link useResearchTrip}, which keys on `queryKeys.trips.detail`
 * — the same entry `features/intake` writes — so it shares one cache rather than importing a sibling
 * feature (the vertical-slice boundary forbids that), and every research invalidation refreshes both.
 */

const POLL_INTERVAL_MS = 2000;

export function useResearchTrip(tripId: string): UseQueryResult<Trip, unknown> {
  return useQuery({
    queryKey: queryKeys.trips.detail(tripId),
    queryFn: ({ signal }) => fetchTrip({ tripId }, signal),
  });
}

export function useStartResearch(): UseMutationResult<ResearchJob, unknown, StartResearchCommand> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: startResearch,
    onSuccess: (job) => {
      queryClient.setQueryData(queryKeys.research.job(job.trip_id, job.job_id), job);
      void queryClient.invalidateQueries({ queryKey: queryKeys.trips.detail(job.trip_id) });
    },
  });
}

export function useResearchJob(tripId: string, jobId: string | null): UseQueryResult<ResearchJob, unknown> {
  const queryClient = useQueryClient();

  return useQuery({
    queryKey: jobId === null ? [...queryKeys.research.all, 'idle'] : queryKeys.research.job(tripId, jobId),
    queryFn: async ({ signal }) => {
      const job = await fetchResearchJob({ tripId, jobId: jobId as string }, signal);
      if (job.status === 'completed' || job.status === 'failed') {
        // The terminal transition also moved the trip's status; refresh it so the brief and any
        // "start research" affordance reflect the new state rather than a stale RESEARCH_RUNNING.
        void queryClient.invalidateQueries({ queryKey: queryKeys.trips.detail(tripId) });
        void queryClient.invalidateQueries({ queryKey: queryKeys.research.recommendations(tripId) });
      }
      return job;
    },
    enabled: jobId !== null,
    refetchInterval: (query) => {
      const job = query.state.data;
      if (job && (job.status === 'queued' || job.status === 'running')) {
        return POLL_INTERVAL_MS;
      }
      return false;
    },
  });
}
