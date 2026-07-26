import type { components } from '@/generated/api/schema';
import { apiRequest } from './client';
import { healthResponseSchema, readinessResponseSchema } from './schemas/health.schema';

/**
 * Platform endpoints. Replaces the hand-written `health-client.ts` stand-in from task 03 — every
 * shape below is an alias of the generated contract, never a re-declaration of it.
 */
export type HealthResponse = components['schemas']['HealthResponse'];
export type ReadinessResponse = components['schemas']['ReadinessResponse'];

export interface HealthRequest {
  signal?: AbortSignal;
}

export async function fetchHealth(request: HealthRequest = {}): Promise<HealthResponse> {
  return apiRequest({
    path: '/health',
    signal: request.signal,
    validate: (payload) => healthResponseSchema.parse(payload),
  });
}

export async function fetchReadiness(request: HealthRequest = {}): Promise<ReadinessResponse> {
  return apiRequest({
    path: '/ready',
    signal: request.signal,
    validate: (payload) => readinessResponseSchema.parse(payload),
  });
}

export type BackendStatus = 'ready' | 'unreachable';

/**
 * View-model probe for the status card.
 *
 * Returns a status instead of throwing: the shell must render without a backend, so an
 * unreachable API is an expected state rather than an error condition. This is the one place a
 * thrown `ApiError` is deliberately flattened — feature code should let it propagate to a query
 * hook and translate `error.i18nKey`.
 */
export async function fetchBackendStatus(signal?: AbortSignal): Promise<BackendStatus> {
  try {
    const health = await fetchHealth({ signal });
    return health.status === 'UP' ? 'ready' : 'unreachable';
  } catch {
    return 'unreachable';
  }
}
