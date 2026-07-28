import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export interface EmptyStateProps {
  title: string;
  description?: string;
  /** The single next action, when one exists. §6.8 asks for one — not a row of choices. */
  action?: ReactNode;
  className?: string;
}

/**
 * Shared empty state (§6.8, PLAN §4.2.6-K).
 *
 * Title and description are required separately rather than as one blob because §11.2 asks an
 * empty state to say *why* it is empty and *what to do next* — a single sentence reliably ends up
 * saying neither.
 */
export function EmptyState({ title, description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center gap-3 rounded-lg border border-border-subtle bg-surface px-4 py-12 text-center',
        className,
      )}
    >
      <h2 className="m-0 text-title text-foreground">{title}</h2>
      {description ? (
        <p className="m-0 max-w-prose text-body-sm text-foreground-muted">{description}</p>
      ) : null}
      {action}
    </div>
  );
}
