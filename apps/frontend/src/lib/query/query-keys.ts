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
  /**
   * Account administration (UC-A15, UC-A16). `users()` is the invalidation prefix: a disable
   * changes both the row in the list and the detail screen, and invalidating one without the other
   * leaves an administrator looking at state the server no longer has.
   */
  admin: {
    all: ['admin'] as const,
    users: () => ['admin', 'users'] as const,
    userPage: (page: number) => ['admin', 'users', 'page', page] as const,
    user: (userId: string) => ['admin', 'users', 'detail', userId] as const,
  },
  /**
   * User-owned trips and C1 brief state (UC-T02, UC-C1-01). The brief key is under its trip so the
   * task 20 SSE invalidation table can clear one trip's mutable planning state without touching the
   * rest of the account.
   */
  trips: {
    all: ['trips'] as const,
    list: () => ['trips', 'list'] as const,
    detail: (tripId: string) => ['trips', 'detail', tripId] as const,
    brief: (tripId: string) => ['trips', 'detail', tripId, 'brief'] as const,
  },
  /**
   * C2 research jobs and outcomes (UC-C2-01/02/03/06). Keyed by trip then job / recommendations so
   * a completing poll can invalidate the trip detail and the ranked list without touching unrelated
   * caches.
   */
  research: {
    all: ['research'] as const,
    job: (tripId: string, jobId: string) => ['research', 'job', tripId, jobId] as const,
    recommendations: (tripId: string) => ['research', 'recommendations', tripId] as const,
    guide: (destinationId: string) => ['research', 'guide', destinationId] as const,
  },
  platform: {
    all: ['platform'] as const,
    health: () => ['platform', 'health'] as const,
    readiness: () => ['platform', 'readiness'] as const,
  },
} as const;
