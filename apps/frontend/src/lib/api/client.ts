import type { paths } from '@/generated/api/schema';
import { ApiError, apiErrorFromResponse } from './api-error';

/**
 * The single entry point for HTTP (PLAN §4.2.2-H). Every `lib/api/<resource>-api.ts` goes through
 * it; nothing else in the app calls `fetch`.
 *
 * Kept deliberately thin. It owns exactly four cross-cutting concerns — base URL, credentials,
 * request correlation, and error translation — and delegates everything else. Response *shapes*
 * come from the generated contract, and response *validation* is injected per call, so this file
 * never needs to know about a resource.
 *
 * The one documented exception to "no raw fetch" is `lib/api/chat-stream.ts` (ADR 007): no
 * generator emits a usable `text/event-stream` client, so the chat event union is hand-authored
 * and mirrored by a backend sealed type. It arrives with task 20.
 */

/** Only paths the contract publishes. A typo is a compile error, not a 404 in production. */
export type ApiPath = keyof paths;

export interface ApiRequest<TResponse> {
  path: ApiPath;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
  /**
   * Runtime response validation (PLAN §6.5). Generated types describe what the server *promises*;
   * this checks what it actually sent. Optional so a caller can opt out deliberately rather than
   * by omission — resource modules pass a zod schema from `lib/api/schemas/`.
   */
  validate?: (payload: unknown) => TResponse;
}

export const REQUEST_ID_HEADER = 'X-Request-Id';

/**
 * Base URL including the `/api/v1` prefix, which is why contract path keys omit it. ADR 006 makes
 * this same-origin in the browser so the session cookie is first-party.
 */
export function apiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';
}

/** Correlation id for one call. The backend echoes it and logs it (PLAN §4.0.2-J2). */
export function newRequestId(): string {
  const webCrypto = globalThis.crypto;
  if (typeof webCrypto?.randomUUID === 'function') {
    return webCrypto.randomUUID();
  }
  // Old Safari and some test environments have no randomUUID. A correlation id only has to be
  // unique enough to find one request in a log file, so a non-cryptographic fallback is fine.
  return `req-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export async function apiRequest<TResponse>(request: ApiRequest<TResponse>): Promise<TResponse> {
  const response = await fetch(`${apiBaseUrl()}${request.path}`, {
    method: request.method ?? 'GET',
    signal: request.signal,
    // Session lives in an httpOnly cookie (ADR 006) — the JWT is never readable from JS, so the
    // credentials flag, not an Authorization header, is what authenticates the call.
    credentials: 'include',
    cache: 'no-store',
    headers: buildHeaders(request.body),
    ...(request.body === undefined ? {} : { body: JSON.stringify(request.body) }),
  });

  if (!response.ok) {
    throw await apiErrorFromResponse(response);
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  const payload: unknown = await response.json();
  return request.validate ? request.validate(payload) : (payload as TResponse);
}

export { ApiError };

function buildHeaders(body: unknown): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    [REQUEST_ID_HEADER]: newRequestId(),
  };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  // TODO(task-08): add the X-XSRF-TOKEN header here once the CSRF cookie exists (ADR 006).
  return headers;
}
