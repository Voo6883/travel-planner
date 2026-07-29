'use client';

import { Button, Input } from 'antd';
import { useTranslations } from 'next-intl';
import { useId, useState, type KeyboardEvent } from 'react';

export interface ChatComposerProps {
  readonly isStreaming: boolean;
  readonly disabled?: boolean;
  readonly onSend: (text: string) => void;
  readonly onStop: () => void;
}

/**
 * The composer (design system §7.3).
 *
 * Three rules from §7.3 are implemented here rather than described:
 *
 * - **Enter sends, Shift+Enter breaks the line.** Both are bound explicitly; leaving Enter to the
 *   textarea's default would make a multi-paragraph message impossible to type.
 * - **Send becomes Stop while streaming**, in the same position, so stopping is one press rather
 *   than a hunt. The draft is not cleared by stopping — "the user can stop generation without
 *   losing prior content".
 * - **The draft survives a failed send.** The field is cleared only after `onSend` has been handed
 *   the text, and a failed message stays in the transcript as "Not sent" with its own retry, so a
 *   user is never asked to retype something the app already has.
 */
export function ChatComposer({ isStreaming, disabled = false, onSend, onStop }: ChatComposerProps) {
  const t = useTranslations('chat');
  const [draft, setDraft] = useState('');
  const hintId = useId();

  const submit = () => {
    const text = draft.trim();
    if (text === '' || isStreaming || disabled) {
      return;
    }
    onSend(text);
    setDraft('');
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      // Not while an IME is composing: in Malay or Chinese input, Enter commits the candidate word
      // and must not also send the message.
      if (event.nativeEvent.isComposing) {
        return;
      }
      event.preventDefault();
      submit();
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-end gap-2">
        <Input.TextArea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          disabled={disabled}
          aria-label={t('composer.label')}
          aria-describedby={hintId}
          placeholder={t('composer.placeholder')}
          // §7.3: 48 px minimum, growing to 160 px and then scrolling internally.
          autoSize={{ minRows: 1, maxRows: 6 }}
          className="min-h-control-lg"
        />
        {isStreaming ? (
          <Button className="min-h-control-lg" onClick={onStop}>
            {t('composer.stop')}
          </Button>
        ) : (
          <Button
            type="primary"
            className="min-h-control-lg"
            disabled={disabled || draft.trim() === ''}
            onClick={submit}
          >
            {t('composer.send')}
          </Button>
        )}
      </div>
      <p id={hintId} className="m-0 text-caption text-foreground-subtle">
        {t('composer.hint')}
      </p>
    </div>
  );
}
