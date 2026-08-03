'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import {
  answerTripBriefClarification,
  fetchTripBrief,
  updateTripBrief,
  type AnswerTripBriefClarificationCommand,
  type TripBrief,
  type UpdateTripBriefCommand,
} from '@/lib/api/trip-api';
import { queryKeys } from '@/lib/query/query-keys';

/**
 * TripBrief server state (UC-C1-01, UC-C1-04).
 *
 * Successful mutations store the server-returned full brief, including the incremented version.
 * Conflicts are left to the editor because only the form knows which field is focused.
 */

export function useTripBrief(tripId: string): UseQueryResult<TripBrief, unknown> {
  return useQuery({
    queryKey: queryKeys.trips.brief(tripId),
    queryFn: ({ signal }) => fetchTripBrief({ tripId }, signal),
  });
}

export function useUpdateTripBrief(): UseMutationResult<TripBrief, unknown, UpdateTripBriefCommand> {
  const setBrief = useTripBriefSetter();

  return useMutation({
    mutationFn: updateTripBrief,
    onSuccess: setBrief,
  });
}

export function useAnswerTripBriefClarification(): UseMutationResult<
  TripBrief,
  unknown,
  AnswerTripBriefClarificationCommand
> {
  const setBrief = useTripBriefSetter();

  return useMutation({
    mutationFn: answerTripBriefClarification,
    onSuccess: setBrief,
  });
}

function useTripBriefSetter(): (brief: TripBrief) => void {
  const queryClient = useQueryClient();

  return (brief) => {
    queryClient.setQueryData(queryKeys.trips.brief(brief.trip_id), brief);
    void queryClient.invalidateQueries({ queryKey: queryKeys.trips.detail(brief.trip_id) });
  };
}
