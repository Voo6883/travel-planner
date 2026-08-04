'use client';

import { useTranslations } from 'next-intl';
import { useEffect } from 'react';
import { ErrorAlert } from '@/components/ui/error-alert';
import type { ChatTarget } from '@/lib/api/chat-api';
import { chatStreamApiError } from '@/lib/api/chat-events';
import { ChatComposer } from './chat-composer';
import { ChatMessageList } from './chat-message-list';
import { ChatStreamingIndicator } from './chat-streaming-indicator';
import { SuggestedPrompts } from './suggested-prompts';
import { useChatStream } from '../hooks/use-chat-stream';

export interface ChatPanelProps {
  readonly target: ChatTarget;
  readonly conversationId?: string | null;
  /**
   * Fired once when the agent commits a trip (PLAN §3.2). The panel does not navigate itself —
   * the route layer owns navigation, and a component that pushed a route would be untestable in
   * isolation and impossible to reuse inside the trip shell where the trip already exists.
   */
  readonly onTripCreated?: (tripId: string) => void;
  /**
   * Fired each turn an intake tool commits a brief edit (task 22, UC-C5-09). The trip shell uses it
   * to invalidate the cached brief and detail so the form beside the chat reflects what the agent
   * just saved. Like {@link onTripCreated} the panel does not fetch itself — the route layer owns
   * the query client.
   */
  readonly onBriefUpdated?: (tripId: string) => void;
  /** Planner home only — 3–4 translated chips that send through the same composer path. */
  readonly showSuggestedPrompts?: boolean;
}

/**
 * The chat surface (design system §7.1): history, live status, composer.
 *
 * Deliberately layout-light. §6.5 gives the panel different widths in the planner and inside a
 * trip, and §4.4 makes it a full-screen layer on mobile; deciding that here would bake one of those
 * into the feature. The panel fills its container and lets the route place it.
 *
 * The error is rendered through the shared `ErrorAlert` rather than as a chat bubble. §6.1 says a
 * failure is a system event, not something the assistant said, and putting `code`/`request_id` in
 * the transcript would make a support detail look like part of the conversation.
 */
export function ChatPanel({
  target,
  conversationId = null,
  onTripCreated,
  onBriefUpdated,
  showSuggestedPrompts = false,
}: ChatPanelProps) {
  const t = useTranslations('chat');
  const chat = useChatStream({ target, conversationId });
  const tripCreatedId = chat.state.tripCreatedId;
  const briefUpdatedTripId = chat.state.briefUpdatedTripId;
  const showPrompts = showSuggestedPrompts && target.scope === 'planner' && chat.messages.length === 0;

  useEffect(() => {
    if (tripCreatedId !== null && onTripCreated) {
      onTripCreated(tripCreatedId);
    }
  }, [onTripCreated, tripCreatedId]);

  useEffect(() => {
    if (briefUpdatedTripId !== null && onBriefUpdated) {
      onBriefUpdated(briefUpdatedTripId);
    }
  }, [onBriefUpdated, briefUpdatedTripId]);

  return (
    <section
      aria-label={t(target.scope === 'planner' ? 'panel.planner_title' : 'panel.trip_title')}
      className="flex h-full min-h-0 flex-col gap-3"
    >
      <ChatMessageList
        messages={chat.messages}
        hasMoreHistory={chat.hasMoreHistory}
        isLoadingHistory={chat.isLoadingHistory}
        onLoadOlder={chat.loadOlderMessages}
        onRetry={chat.retry}
      />

      {showPrompts ? (
        <SuggestedPrompts
          disabled={chat.isStreaming}
          onSelect={(text) => {
            void chat.send(text);
          }}
        />
      ) : null}

      {tripCreatedId === null ? null : (
        // §7.4: "preserve the conversation and show a brief transition".
        <p role="status" className="m-0 rounded-md bg-success-surface px-3 py-2 text-caption text-foreground">
          {t('status.trip_created')}
        </p>
      )}

      {chat.error === null ? null : <ErrorAlert error={chatStreamApiError(chat.error)} onRetry={chat.retry} />}

      <ChatStreamingIndicator connection={chat.state.connection} tools={chat.state.tools} />
      <ChatComposer isStreaming={chat.isStreaming} onSend={chat.send} onStop={chat.stop} />
    </section>
  );
}
