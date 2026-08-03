'use client';

import { tripChatTarget } from '@/lib/api/chat-api';
import { ChatPanel } from './chat-panel';

export interface TripChatPanelProps {
  readonly tripId: string;
}

/** Persistent trip conversation after planner handoff (PLAN §3.2). */
export function TripChatPanel({ tripId }: TripChatPanelProps) {
  return (
    <div className="min-h-[24rem]">
      <ChatPanel target={tripChatTarget(tripId)} />
    </div>
  );
}
