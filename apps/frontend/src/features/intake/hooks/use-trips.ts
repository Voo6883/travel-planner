'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import {
  archiveTrip,
  createTrip,
  deleteTrip,
  fetchTrip,
  fetchTrips,
  renameTrip,
  type ArchiveTripCommand,
  type CreateTripRequest,
  type RenameTripCommand,
  type Trip,
  type TripIdCommand,
  type TripList,
} from '@/lib/api/trip-api';
import { queryKeys } from '@/lib/query/query-keys';

/**
 * Trip lifecycle hooks (UC-T01b–T06).
 *
 * Mutations invalidate or write server responses only. Trips are agent-visible state from task 19
 * onward, so optimistic rows are intentionally absent (ADR 008 §5).
 */

export function useTrips(): UseQueryResult<TripList, unknown> {
  return useQuery({
    queryKey: queryKeys.trips.list(),
    queryFn: ({ signal }) => fetchTrips(signal),
  });
}

export function useTrip(tripId: string): UseQueryResult<Trip, unknown> {
  return useQuery({
    queryKey: queryKeys.trips.detail(tripId),
    queryFn: ({ signal }) => fetchTrip({ tripId }, signal),
  });
}

export function useCreateTrip(): UseMutationResult<Trip, unknown, CreateTripRequest> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createTrip,
    onSuccess: (trip) => {
      queryClient.setQueryData(queryKeys.trips.detail(trip.trip_id), trip);
      void queryClient.invalidateQueries({ queryKey: queryKeys.trips.list() });
    },
  });
}

export function useRenameTrip(): UseMutationResult<Trip, unknown, RenameTripCommand> {
  const updateTrip = useTripUpdater();

  return useMutation({
    mutationFn: renameTrip,
    onSuccess: updateTrip,
  });
}

export function useArchiveTrip(): UseMutationResult<Trip, unknown, ArchiveTripCommand> {
  const updateTrip = useTripUpdater();

  return useMutation({
    mutationFn: archiveTrip,
    onSuccess: updateTrip,
  });
}

export function useDeleteTrip(): UseMutationResult<void, unknown, TripIdCommand> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteTrip,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.trips.all });
    },
  });
}

function useTripUpdater(): (trip: Trip) => void {
  const queryClient = useQueryClient();

  return (trip) => {
    queryClient.setQueryData(queryKeys.trips.detail(trip.trip_id), trip);
    void queryClient.invalidateQueries({ queryKey: queryKeys.trips.list() });
  };
}
