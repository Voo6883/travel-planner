'use client';

import { useTranslations } from 'next-intl';
import { cn } from '@/lib/utils/cn';
import type { ChatMessage, ChatMessageStatus } from '../lib/chat-state';

export interface ChatMessageItemProps {
  readonly message: ChatMessage;
  /** Offered on a message the server never accepted (§7.3). Retries the original client id. */
  readonly onRetry?: () => void;
}

/**
 * One bubble (design system §7.2).
 *
 * <b>Text is rendered as text.</b> `{message.text}` is a React text child, so React escapes it —
 * there is no `dangerouslySetInnerHTML` here and there must not be one. Task 20 ships "plain safe
 * text only"; **task 36 owns Markdown rendering and its sanitisation** (ADR 007: sanitised
 * server-side on persist, DOMPurify on completed messages, never per token frame). Until that
 * lands, a model that emits `<img onerror=…>` produces those characters on screen, which is the
 * correct behaviour rather than a limitation.
 *
 * `whitespace-pre-wrap` is what makes plain text readable — the model's paragraph breaks are real
 * newlines, and collapsing them turns a structured answer into a wall.
 *
 * Delivery state is shown only when it means something. "Sent" on every settled bubble is noise;
 * "Not sent", "Interrupted" and "Stopped" are the states a user has to act on or account for, and
 * §10.1 requires each to be readable as words rather than inferred from a colour.
 */
export function ChatMessageItem({ message, onRetry }: ChatMessageItemProps) {
  const t = useTranslations('chat');
  const isUser = message.role === 'user';
  const isToolRow =
    message.role === 'tool_call' || message.role === 'tool_result' || message.role === 'lifecycle_event';
  const status = visibleStatus(message.status);

  return (
    <li className={cn('flex w-full flex-col gap-1', isUser ? 'items-end' : 'items-start')}>
      <div
        className={cn(
          'max-w-[85%] rounded-xl px-4 py-3 text-body-sm md:max-w-[78%]',
          isUser
            ? 'bg-chat-user-surface text-chat-user-text'
            : 'max-w-[92%] bg-surface-subtle text-foreground md:max-w-[88%]',
          message.role === 'system' && 'border border-border-subtle bg-transparent text-foreground-muted',
          isToolRow && 'border border-dashed border-border-subtle bg-transparent text-foreground-muted',
        )}
      >
        {/* Every bubble keeps an accessible sender label even when grouping hides it visually. */}
        <span className="sr-only">{t(`sender.${message.role}`)}</span>
        {/* Tool / lifecycle rows must never dump raw JSON (§7.2); tasks 21/22 own richer chrome. */}
        <p className="m-0 whitespace-pre-wrap break-words">{isToolRow ? t('tools.hidden_row') : message.text}</p>
      </div>
      {status === null ? null : (
        <p className="m-0 flex items-center gap-2 text-caption text-foreground-muted">
          <span>{t(`status.${status}`)}</span>
          {status === 'failed' && onRetry ? (
            <button
              type="button"
              onClick={onRetry}
              className="min-h-control text-caption text-action-primary-text underline"
            >
              {t('actions.retry')}
            </button>
          ) : null}
        </p>
      )}
    </li>
  );
}

/** Settled, unremarkable states say nothing; the rest have to. */
function visibleStatus(status: ChatMessageStatus): ChatMessageStatus | null {
  return status === 'sent' || status === 'complete' || status === 'streaming' ? null : status;
}
