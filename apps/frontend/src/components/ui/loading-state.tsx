import { cn } from '@/lib/utils/cn';

export interface LoadingStateProps {
  /** Translated description of what is loading, announced to screen readers. */
  label: string;
  /** How many placeholder rows to draw. Match the final layout, not a fixed number (§6.8). */
  rows?: number;
  className?: string;
}

/**
 * Skeleton placeholder (§6.8): "skeleton matching final layout; no spinner-only blank page".
 *
 * The bars are `aria-hidden` and the real announcement is a visually hidden `role="status"`.
 * Screen-reader users get "Loading your session" once, instead of a stack of anonymous boxes —
 * and the polite live region means it does not interrupt whatever they were reading.
 *
 * The shimmer is a plain `animate-pulse`; `prefers-reduced-motion` disables it globally in
 * `globals.css`, so no component has to remember to.
 */
export function LoadingState({ label, rows = 3, className }: LoadingStateProps) {
  return (
    <div className={cn('flex w-full flex-col gap-3', className)}>
      <span role="status" aria-live="polite" className="sr-only">
        {label}
      </span>
      {Array.from({ length: rows }, (_, index) => (
        <div
          key={index}
          aria-hidden="true"
          className={cn(
            'h-4 animate-pulse rounded-md bg-surface-subtle',
            index === 0 ? 'w-1/3' : 'w-full',
          )}
        />
      ))}
    </div>
  );
}
