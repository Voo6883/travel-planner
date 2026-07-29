'use client';

import { useTranslations } from 'next-intl';
import type { ChatConnectionState, ChatToolActivity } from '../lib/chat-state';

export interface ChatStreamingIndicatorProps {
  readonly connection: ChatConnectionState;
  readonly tools: readonly ChatToolActivity[];
}

/**
 * The one live region for turn progress (design system §7.4, §9.3).
 *
 * `aria-live="polite"` announces *state*, never tokens. Announcing each delta would make a screen
 * reader read a sentence letter by letter as it is composed — §7.4 asks for completion to be
 * announced, not the typing.
 *
 * Tool activity collapses to a single user-facing line (§7.2: "Never display internal tool
 * identifiers or raw JSON"). The tool's name is not rendered — `search_flights` is an
 * implementation detail, and the running-count is all the user needs to know something is
 * happening.
 */
export function ChatStreamingIndicator({ connection, tools }: ChatStreamingIndicatorProps) {
  const t = useTranslations('chat');
  const running = tools.some((tool) => tool.running);
  const label = statusLabel(connection, running);

  return (
    <p aria-live="polite" className="m-0 min-h-5 text-caption text-foreground-muted">
      {label === null ? null : (
        <>
          {/* §3.6/§5: a 6 px pulse rather than a bouncing ellipsis. */}
          <span
            aria-hidden="true"
            className="mr-2 inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-ai-accent-text"
          />
          {t(label)}
        </>
      )}
    </p>
  );
}

function statusLabel(connection: ChatConnectionState, toolRunning: boolean): string | null {
  if (connection === 'reconnecting') {
    return 'status.reconnecting';
  }
  if (connection !== 'streaming') {
    return null;
  }
  return toolRunning ? 'tools.running' : 'status.streaming';
}
