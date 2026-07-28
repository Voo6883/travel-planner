import Link from 'next/link';
import type { AuthCardProps } from '../types';

/**
 * The shell every authentication screen shares (§8.2): centred card, 420 px maximum, on `canvas`,
 * brand mark and concise heading above the form, no photography behind it.
 *
 * One shell for sign-in, sign-up, forgot-password, reset-password, and verify-email is what §8.2
 * means by "forgot-password uses the same card shell" — a user moving between them sees the same
 * frame, so only the task changes.
 *
 * Server component: it holds no state. The panels inside it are the client boundary, which keeps
 * the JavaScript on an unauthenticated page to what the form actually needs.
 */
export function AuthCard({ title, description, children, footer }: AuthCardProps) {
  return (
    <div className="flex min-h-dvh w-full flex-col items-center justify-center bg-canvas px-4 py-10">
      <div className="w-full max-w-auth-card">
        <Link
          href="/"
          className="mb-6 flex min-h-control items-center justify-center text-title text-foreground no-underline"
        >
          {/* The brand mark. A pin glyph reads at 16 px and needs no image request (§3.5). */}
          <span aria-hidden="true" className="mr-2 text-h3 text-action-primary-text">
            ◈
          </span>
          Travel Planner
        </Link>

        <main
          id="main-content"
          className="rounded-lg border border-border-subtle bg-surface p-6 shadow-sm"
        >
          <h1 className="m-0 text-h1-mobile text-foreground sm:text-h1">{title}</h1>
          {description ? (
            <p className="mb-0 mt-2 text-body-sm text-foreground-muted">{description}</p>
          ) : null}
          <div className="mt-6">{children}</div>
        </main>

        {footer ? <div className="mt-6 text-center text-body-sm">{footer}</div> : null}
      </div>
    </div>
  );
}
