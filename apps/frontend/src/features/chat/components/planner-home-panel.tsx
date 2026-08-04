'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useCallback } from 'react';
import { PLANNER_CHAT_TARGET } from '@/lib/api/chat-api';
import { queryKeys } from '@/lib/query/query-keys';
import { ChatPanel } from './chat-panel';

/**
 * Planner home chat surface (PLAN §3.2, UC-C5-00/01, design system §8.3).
 *
 * Navigation is driven only by the typed `trip_created` SSE event — never by parsing assistant
 * prose. Trip list cache is invalidated so a chat-created trip appears beside the composer.
 */
export function PlannerHomePanel() {
  const t = useTranslations('chat.home');
  const router = useRouter();
  const queryClient = useQueryClient();

  const onTripCreated = useCallback(
    (tripId: string) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.trips.all });
      router.push(`/trips/${tripId}`);
    },
    [queryClient, router],
  );

  return (
    <div className="mx-auto flex w-full max-w-[720px] flex-col gap-4">
      <header className="flex flex-col gap-2">
        <h2 className="m-0 text-title text-foreground">{t('heading')}</h2>
        <p className="m-0 text-body text-muted-foreground">{t('intro')}</p>
      </header>

      <div className="min-h-[28rem] flex-1">
        <ChatPanel target={PLANNER_CHAT_TARGET} onTripCreated={onTripCreated} showSuggestedPrompts />
      </div>
    </div>
  );
}
