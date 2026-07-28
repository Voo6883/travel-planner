import type { ReactNode } from 'react';

/**
 * `(auth)` shell — deliberately empty.
 *
 * PLAN §4.2.3 describes it as a "minimal shell — no planner sidebar", and §8.2 puts the whole
 * visual frame in the centred card itself. Adding navigation here would offer a signed-out
 * visitor links to pages the guard will bounce them straight back from.
 *
 * It exists as a route group so that `(planner)` and `(admin)` can carry a guard while these
 * routes stay public — the mailed verification and reset links must open without a session.
 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
