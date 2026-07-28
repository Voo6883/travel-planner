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
 * Double-submit CSRF pair (ADR 006). Spring Security writes `XSRF-TOKEN` as a *readable* cookie —
 * unlike the session, which is httpOnly — and expects it echoed in `X-XSRF-TOKEN` on every
 * mutating request. Reading a cookie is the whole mechanism: an attacker's page can make the
 * browser send our session cookie, but the same-origin policy stops it reading this value.
 */
export const CSRF_COOKIE = 'XSRF-TOKEN';
export const CSRF_HEADER = 'X-XSRF-TOKEN';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/**
 * The token is seeded by any `GET` (ADR 006), so by the time a user can trigger a mutation the
 * cookie exists. Returns null during server rendering, where there is no cookie jar and no CSRF
 * risk to mitigate.
 */
export function csrfToken(): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${CSRF_COOKIE}=`));
  return match ? decodeURIComponent(match.slice(CSRF_COOKIE.length + 1)) : null;
}

/** ADR 006 default. Relative, so the browser resolves it against the frontend's own origin. */
export const DEFAULT_API_BASE_PATH = '/api/v1';

/**
 * Base URL including the `/api/v1` prefix, which is why contract path keys omit it.
 *
 * **In the browser this is relative** (ADR 006). That is the whole fix for F-18: `tp_session` is
 * `httpOnly; SameSite=Lax`, so a cross-origin request to `localhost:8080` would silently drop it
 * and every authenticated call would fail. A relative URL is same-origin by construction, and
 * `next.config.ts` `rewrites()` forwards it to Spring Boot.
 *
 * On the server there is no origin to resolve a relative URL against, so the server-only
 * `BACKEND_INTERNAL_URL` is prefixed instead — `http://backend:8080` in Compose,
 * `http://localhost:8080` on the host. It is deliberately not `NEXT_PUBLIC_*`: the browser must
 * never learn a second, cookie-less way to reach the API.
 */
export function apiBaseUrl(): string {
  // `||`, not `??`: an env var set to the empty string is unset in every way that matters, and a
  // base URL of `''` would silently turn `/auth/login` into a request against the page's path.
  const configured = process.env.NEXT_PUBLIC_API_BASE_URL || DEFAULT_API_BASE_PATH;
  if (isAbsoluteUrl(configured) || typeof window !== 'undefined') {
    return configured;
  }
  const internal = (process.env.BACKEND_INTERNAL_URL || 'http://localhost:8080').replace(/\/+$/, '');
  return `${internal}${configured}`;
}

function isAbsoluteUrl(value: string): boolean {
  return /^https?:\/\//i.test(value);
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
  const method = request.method ?? 'GET';
  const response = await fetch(`${apiBaseUrl()}${request.path}`, {
    method,
    signal: request.signal,
    // Session lives in an httpOnly cookie (ADR 006) — the JWT is never readable from JS, so the
    // credentials flag, not an Authorization header, is what authenticates the call.
    credentials: 'include',
    cache: 'no-store',
    headers: buildHeaders(method, request.body),
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

function buildHeaders(method: string, body: unknown): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    [REQUEST_ID_HEADER]: newRequestId(),
  };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (!SAFE_METHODS.has(method)) {
    // Mutating requests only. Sending it on a GET would be harmless but pointless — and it is
    // exactly the safe/unsafe split Spring Security's CsrfFilter applies on the other side.
    const token = csrfToken();
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }
  return headers;
}
