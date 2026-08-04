'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import {
  fetchRankedRecommendations,
  selectRecommendation,
  type RankedRecommendations,
  type SelectRecommendationCommand,
} from '@/lib/api/research-api';
import type { Trip } from '@/lib/api/trip-api';
import { queryKeys } from '@/lib/query/query-keys';

/**
 * Ranked recommendations and destination selection (UC-C2-03/05/06).
 *
 * Selection waits for the server response before treating a card as selected — no optimistic UI
 * (task 26 Do-not). On success the trip detail and recommendations caches are invalidated together.
 */

export function useRankedRecommendations(
  tripId: string,
  enabled: boolean,
): UseQueryResult<RankedRecommendations, unknown> {
  return useQuery({
    queryKey: queryKeys.research.recommendations(tripId),
    queryFn: ({ signal }) => fetchRankedRecommendations({ tripId }, signal),
    enabled,
  });
}

export function useSelectRecommendation(): UseMutationResult<Trip, unknown, SelectRecommendationCommand> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: selectRecommendation,
    onSuccess: (trip) => {
      queryClient.setQueryData(queryKeys.trips.detail(trip.trip_id), trip);
      void queryClient.invalidateQueries({
        queryKey: queryKeys.research.recommendations(trip.trip_id),
      });
    },
  });
}
