import type { ReactNode } from 'react';

interface PageShellProps {
  title: string;
  description?: string;
  children: ReactNode;
}

/**
 * Standalone page shell — used where there is no `AppShell` around it (the marketing landing).
 *
 * It owns the `<main id="main-content">` landmark, which is what the skip link targets. Inside
 * the authenticated shell that landmark already exists, so those pages use `PageHeader` instead;
 * nesting two `main` elements would give the page two "main content" destinations and break the
 * skip link.
 *
 * Padding follows the §4.2 page-shell convention exactly. Fluid widths only — nothing here may
 * force a horizontal scrollbar at the 320 px floor.
 */
export function PageShell({ title, description, children }: PageShellProps) {
  return (
    <div className="min-h-dvh w-full">
      <header className="w-full border-b border-border-subtle bg-surface">
        <div className="mx-auto w-full max-w-7xl px-4 py-6 md:px-6 md:py-8 xl:px-8">
          <h1 className="m-0 text-h1-mobile text-foreground sm:text-h1">{title}</h1>
          {description ? (
            <p className="mb-0 mt-2 max-w-prose text-body-sm text-foreground-muted">{description}</p>
          ) : null}
        </div>
      </header>

      <main
        id="main-content"
        className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 md:px-6 md:py-8 xl:px-8"
      >
        {children}
      </main>
    </div>
  );
}
