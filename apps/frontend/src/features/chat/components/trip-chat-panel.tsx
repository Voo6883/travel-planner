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
 * When an intake tool commits a brief edit the server emits `brief_updated` (task 22, UC-C5-09);
 * this invalidates the cached brief and the trip detail so the editor beside the chat re-fetches
 * what the agent saved rather than showing the value the traveller last typed into the form.
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

  return (
    <div className="min-h-[24rem]">
      <ChatPanel target={tripChatTarget(tripId)} onBriefUpdated={onBriefUpdated} />
    </div>
  );
}
