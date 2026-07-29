'use client';

import { Button } from 'antd';
import { useTranslations } from 'next-intl';
import { EmptyState } from '@/components/ui/empty-state';
import { ChatMessageItem } from './chat-message-item';
import type { ChatMessage } from '../lib/chat-state';

export interface ChatMessageListProps {
  readonly messages: readonly ChatMessage[];
  readonly hasMoreHistory: boolean;
  readonly isLoadingHistory: boolean;
  readonly onLoadOlder: () => void;
  readonly onRetry: () => void;
}

/**
 * The scrollable history (design system §7.1).
 *
 * A named `role="log"` region, which is what tells assistive technology that new children are
 * appended updates rather than a re-rendered page. Each message keeps its own accessible sender
 * label so the reading order carries sender, content and delivery state without relying on the
 * left/right alignment that conveys it visually.
 *
 * Pagination reaches backwards: "Load earlier messages" sits at the top because that is where the
 * conversation continues into the past, and it is a real button rather than an infinite scroll —
 * §9.2 wants a keyboard-focusable control for anything that changes what is on screen.
 */
export function ChatMessageList({
  messages,
  hasMoreHistory,
  isLoadingHistory,
  onLoadOlder,
  onRetry,
}: ChatMessageListProps) {
  const t = useTranslations('chat');

  if (messages.length === 0 && !isLoadingHistory) {
    return <EmptyState title={t('panel.empty_title')} description={t('panel.empty_body')} />;
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto">
      {hasMoreHistory ? (
        <Button type="link" className="self-center" loading={isLoadingHistory} onClick={onLoadOlder}>
          {t('panel.load_older')}
        </Button>
      ) : null}
      <ol role="log" aria-label={t('panel.log_label')} className="m-0 flex list-none flex-col gap-3 p-0">
        {messages.map((message) => (
          <ChatMessageItem key={message.key} message={message} onRetry={onRetry} />
        ))}
      </ol>
    </div>
  );
}
