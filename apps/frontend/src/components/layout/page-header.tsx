import type { ReactNode } from 'react';

export interface PageHeaderProps {
  title: string;
  description?: string;
  /** The one primary action for this page, when there is one (§6.1). */
  action?: ReactNode;
  children: ReactNode;
}

/**
 * A page **inside** `AppShell` (§4.2, §8.10 "page header provides title, result count, and
 * approved actions").
 *
 * Unlike `PageShell` it renders no `main` landmark — the shell already owns one — and it applies
 * the §4.2 padding convention plus the `gap-6` vertical rhythm, so no page has to remember them.
 *
 * Exactly one `h1` per page (§10.1), and it lives here rather than in each screen so that rule
 * holds by construction.
 */
export function PageHeader({ title, description, action, children }: PageHeaderProps) {
  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 md:px-6 md:py-8 xl:px-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="m-0 text-h1-mobile text-foreground sm:text-h1">{title}</h1>
          {description ? (
            <p className="mb-0 mt-2 max-w-prose text-body-sm text-foreground-muted">{description}</p>
          ) : null}
        </div>
        {action}
      </div>
      {children}
    </div>
  );
}
