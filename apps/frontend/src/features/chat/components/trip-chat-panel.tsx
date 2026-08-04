'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { tripChatTarget } from '@/lib/api/chat-api';
import { queryKeys } from '@/lib/query/query-keys';
import { ChatPanel } from './chat-panel';

export interface TripChatPanelProps {
  readonly tripId: string;
}

/**
 * Persistent trip conversation after planner handoff (PLAN §3.2).
 *
 * Domain SSE events keep the structured screens beside chat in sync (tasks 22 and 27):
 * `brief_updated`, `research_started`, `destination_selected`.
 */
export function TripChatPanel({ tripId }: TripChatPanelProps) {
  const queryClient = useQueryClient();

  const onBriefUpdated = useCallback(
    (updatedTripId: string) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.trips.brief(updatedTripId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.trips.detail(updatedTripId) });
    },
    [queryClient],
  );

  const onResearchStarted = useCallback(
    (payload: { tripId: string; jobId: string }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.trips.detail(payload.tripId) });
      void queryClient.invalidateQueries({
        queryKey: queryKeys.research.job(payload.tripId, payload.jobId),
      });
    },
    [queryClient],
  );

  const onDestinationSelected = useCallback(
    (selectedTripId: string) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.trips.detail(selectedTripId) });
      void queryClient.invalidateQueries({
        queryKey: queryKeys.research.recommendations(selectedTripId),
      });
    },
    [queryClient],
  );

  return (
    <div className="min-h-[24rem]">
      <ChatPanel
        target={tripChatTarget(tripId)}
        onBriefUpdated={onBriefUpdated}
        onResearchStarted={onResearchStarted}
        onDestinationSelected={onDestinationSelected}
      />
    </div>
  );
}
