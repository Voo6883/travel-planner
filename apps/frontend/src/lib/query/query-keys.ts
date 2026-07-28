/**
 * The single query-key vocabulary (PLAN §4.2.6-G). Inline string arrays are forbidden: a typo in
 * one of them produces a second cache entry that never invalidates, and that failure is invisible
 * until a user sees stale data.
 *
 * Keys are hierarchical so a prefix invalidates a subtree — `invalidateQueries({ queryKey:
 * queryKeys.auth.all })` clears the session *and* everything derived from it.
 *
 * Feature namespaces (trips, research, itinerary, booking, chat) are added by their owning tasks.
 */
export const queryKeys = {
  auth: {
    all: ['auth'] as const,
    currentUser: () => ['auth', 'me'] as const,
  },
  platform: {
    all: ['platform'] as const,
    health: () => ['platform', 'health'] as const,
    readiness: () => ['platform', 'readiness'] as const,
  },
} as const;
