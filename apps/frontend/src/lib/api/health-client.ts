import { z } from 'zod';

/**
 * TEMPORARY health client — replaced wholesale by generated code in
 * tasks/06-openapi-error-platform.md.
 *
 * This is the *only* hand-written request shape permitted in the frontend, and it exists solely
 * so the Task 03 shell can show whether the backend is up. It is deliberately confined to
 * lib/api/ (PLAN §4.2.2): pages and components never call fetch directly.
 *
 * REPLACEMENT POINT (task 06): delete this file. Import the generated client from
 * `@/generated/api` and keep `BackendStatus` as the view-model if the status card still needs it.
 * Do not grow this file with more endpoints — that is the drift the codegen rule prevents.
 */

/** Mirrors the backend HealthResponse. Validated at the boundary, never trusted raw. */
const healthResponseSchema = z.object({
  status: z.string(),
});

export type BackendStatus = 'ready' | 'unreachable';

function baseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';
}

/**
 * Returns a status rather than throwing: the shell must render without the backend, so an
 * unreachable API is an expected state, not an error condition.
 */
export async function fetchBackendStatus(signal?: AbortSignal): Promise<BackendStatus> {
  try {
    const response = await fetch(`${baseUrl()}/health`, {
      signal,
      headers: { Accept: 'application/json' },
      // Cookie-based auth arrives in task 08; ADR 006 makes this same-origin.
      credentials: 'include',
      cache: 'no-store',
    });

    if (!response.ok) {
      return 'unreachable';
    }

    const parsed = healthResponseSchema.safeParse(await response.json());
    return parsed.success && parsed.data.status === 'UP' ? 'ready' : 'unreachable';
  } catch {
    return 'unreachable';
  }
}
