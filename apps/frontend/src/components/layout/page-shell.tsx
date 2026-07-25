import type { ReactNode } from 'react';

interface PageShellProps {
  title: string;
  description?: string;
  children: ReactNode;
}

/**
 * Minimal page shell: header, main landmark, constrained content column.
 *
 * Fluid widths only — nothing here may force a horizontal scrollbar at the 320 px floor
 * (docs/UI-UX-DESIGN-SYSTEM.md §4.1). The full app shell with sidebar, trip context, and
 * stepper belongs to tasks/11 and tasks/31.
 */
export function PageShell({ title, description, children }: PageShellProps) {
  return (
    <div className="min-h-dvh w-full">
      <header className="w-full border-b border-border-subtle bg-surface">
        <div className="mx-auto w-full max-w-5xl px-4 py-4 sm:px-6">
          <h1 className="text-lg font-semibold sm:text-xl">{title}</h1>
          {description ? (
            <p className="mt-1 max-w-prose text-sm text-foreground-muted">{description}</p>
          ) : null}
        </div>
      </header>

      <main id="main-content" className="mx-auto w-full max-w-5xl px-4 py-6 sm:px-6">
        {children}
      </main>
    </div>
  );
}
