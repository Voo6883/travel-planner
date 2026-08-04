'use client';

import { Button } from 'antd';
import { useTranslations } from 'next-intl';

const PROMPT_KEYS = ['plan_trip', 'weekend_getaway', 'surprise_me', 'family_trip'] as const;

export type SuggestedPromptKey = (typeof PROMPT_KEYS)[number];

export interface SuggestedPromptsProps {
  readonly disabled?: boolean;
  readonly onSelect: (text: string) => void;
}

/**
 * Planner-home prompt chips (design system §8.3). Selecting one fills the composer path via
 * `onSelect` — the parent owns send so a suggestion never bypasses chat idempotency.
 */
export function SuggestedPrompts({ disabled = false, onSelect }: SuggestedPromptsProps) {
  const t = useTranslations('chat.suggestions');

  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label={t('label')}>
      {PROMPT_KEYS.map((key) => (
        <Button
          key={key}
          type="default"
          size="middle"
          disabled={disabled}
          className="min-h-control text-left"
          onClick={() => onSelect(t(`prompts.${key}`))}
        >
          {t(`prompts.${key}`)}
        </Button>
      ))}
    </div>
  );
}
