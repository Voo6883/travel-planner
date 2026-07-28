import { Suspense } from 'react';
import { SignInPanel } from '@/features/auth';

/**
 * `/sign-in` — UC-A04, and the landing for a failed GitHub round trip (`?error=<code>`).
 *
 * Composition only (PLAN §4.2.6-D). The `Suspense` boundary is required, not decorative: the
 * panel reads `useSearchParams`, and Next.js refuses to prerender a page that does so without
 * one.
 */
export default function SignInPage() {
  return (
    <Suspense>
      <SignInPanel />
    </Suspense>
  );
}
