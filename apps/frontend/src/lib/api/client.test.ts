import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiBaseUrl, apiRequest, CSRF_HEADER, newRequestId } from './client';

/**
 * The client boundary is the only place the app talks HTTP, so the properties asserted here —
 * credentials, correlation, error translation, runtime validation — hold for every future call
 * without each feature re-implementing them.
 */

interface StubResponseInit {
  status?: number;
  body?: unknown;
  requestId?: string;
}

function stubResponse(init: StubResponseInit = {}): Response {
  const status = init.status ?? 200;
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(init.requestId ? { 'X-Request-Id': init.requestId } : {}),
    json: async () => init.body,
  } as Response;
}

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

describe('apiRequest', () => {
  it('sends the cookie session and a correlation id on every call', async () => {
    const fetchMock = stubFetch(stubResponse({ body: { status: 'UP' } }));

    await apiRequest({ path: '/health' });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/health');
    // ADR 006: the JWT is httpOnly, so credentials — not an Authorization header — authenticate.
    expect(init.credentials).toBe('include');
    expect((init.headers as Record<string, string>)['X-Request-Id']).toBeTruthy();
    expect(init.method).toBe('GET');
  });

  it('sends a JSON body and content type only when there is a body', async () => {
    const fetchMock = stubFetch(stubResponse({ body: {} }));

    await apiRequest({ path: '/health', method: 'POST', body: { expected_version: 7 } });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.body).toBe('{"expected_version":7}');
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('echoes the CSRF cookie on mutating requests but not on safe ones (ADR 006)', async () => {
    // Spring Security writes XSRF-TOKEN as a readable cookie precisely so the page can echo it.
    // A cross-site page can make the browser send our session cookie, but the same-origin policy
    // stops it reading this value — which is what makes the double submit a proof of origin.
    document.cookie = 'XSRF-TOKEN=token-from-server';
    const fetchMock = stubFetch(stubResponse({ body: {} }));

    await apiRequest({ path: '/health', method: 'POST', body: {} });
    await apiRequest({ path: '/health' });

    const [, mutating] = fetchMock.mock.calls[0] as [string, RequestInit];
    const [, safe] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect((mutating.headers as Record<string, string>)[CSRF_HEADER]).toBe('token-from-server');
    expect((safe.headers as Record<string, string>)[CSRF_HEADER]).toBeUndefined();
  });

  it('omits the CSRF header when no token has been issued yet', async () => {
    document.cookie = 'XSRF-TOKEN=; max-age=0';
    const fetchMock = stubFetch(stubResponse({ body: {} }));

    await apiRequest({ path: '/health', method: 'POST', body: {} });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)[CSRF_HEADER]).toBeUndefined();
  });

  it('runs the injected validator so a malformed payload cannot reach a component', async () => {
    stubFetch(stubResponse({ body: { unexpected: true } }));

    await expect(
      apiRequest({
        path: '/health',
        validate: () => {
          throw new Error('schema mismatch');
        },
      }),
    ).rejects.toThrow('schema mismatch');
  });

  it('translates the error envelope into a typed ApiError', async () => {
    stubFetch(
      stubResponse({
        status: 409,
        requestId: 'req-42',
        body: {
          code: 'version_conflict',
          message: 'The resource was modified by someone else.',
          details: { current_version: 9 },
        },
      }),
    );

    const error = await apiRequest({ path: '/health' }).catch((thrown: unknown) => thrown);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.code).toBe('version_conflict');
    expect(apiError.i18nKey).toBe('errors.version_conflict');
    expect(apiError.currentVersion).toBe(9);
    expect(apiError.requestId).toBe('req-42');
  });

  it('exposes field errors from a validation failure for form highlighting', async () => {
    stubFetch(
      stubResponse({
        status: 400,
        body: {
          code: 'validation_failed',
          message: 'The request is not valid.',
          details: { fields: { expected_version: 'must not be null', ignored: 7 } },
        },
      }),
    );

    const error = (await apiRequest({ path: '/health' }).catch((thrown) => thrown)) as ApiError;

    expect(error.fieldErrors).toEqual({ expected_version: 'must not be null' });
  });

  it('does not let a proxy decide what the UI says', async () => {
    // A gateway 502 returns HTML, not the envelope. Anything unrecognised becomes internal_error
    // so the user still gets a translated message instead of a raw or attacker-chosen code.
    stubFetch(stubResponse({ status: 502, body: '<html>Bad Gateway</html>' }));

    const error = (await apiRequest({ path: '/health' }).catch((thrown) => thrown)) as ApiError;

    expect(error.code).toBe('internal_error');
    expect(error.i18nKey).toBe('errors.internal_error');
  });

  it('treats an unregistered code as internal_error rather than rendering a raw key', async () => {
    stubFetch(stubResponse({ status: 418, body: { code: 'brand_new_code', message: 'nope' } }));

    const error = (await apiRequest({ path: '/health' }).catch((thrown) => thrown)) as ApiError;

    expect(error.i18nKey).toBe('errors.internal_error');
  });
});

/**
 * ADR 006 — the fix for follow-up F-18.
 *
 * `tp_session` is `httpOnly; SameSite=Lax`. A `Lax` cookie is sent only on same-site requests and
 * top-level GET navigations, so a cross-origin `fetch('http://localhost:8080/api/v1/…')` from
 * `localhost:3000` carries **no session** — every authenticated request fails, from the first
 * login attempt onward. These assertions are the regression guard: if the base URL ever becomes
 * absolute again in the browser, browser authentication breaks silently and this fails loudly.
 */
describe('same-origin API base URL (ADR 006)', () => {
  it('issues a relative, same-origin URL in the browser', async () => {
    const fetchMock = stubFetch(stubResponse({ body: { status: 'UP' } }));

    await apiRequest({ path: '/health' });

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/health');
    // Any scheme or protocol-relative prefix here means the request leaves this origin.
    expect(url.startsWith('/')).toBe(true);
    expect(url.startsWith('//')).toBe(false);
    expect(url).not.toMatch(/^[a-z]+:/i);
  });

  it('defaults to the relative path when NEXT_PUBLIC_API_BASE_URL is unset', () => {
    vi.stubEnv('NEXT_PUBLIC_API_BASE_URL', '');

    expect(apiBaseUrl()).toBe('/api/v1');
  });

  it('prefixes BACKEND_INTERNAL_URL on the server, where a relative URL cannot resolve', () => {
    // There is no origin to resolve against during SSR, so the server-only variable supplies one.
    // It is deliberately not NEXT_PUBLIC_*: the browser must never learn a cookie-less route in.
    vi.stubGlobal('window', undefined);
    vi.stubEnv('BACKEND_INTERNAL_URL', 'http://backend:8080');

    expect(apiBaseUrl()).toBe('http://backend:8080/api/v1');
  });
});

describe('newRequestId', () => {
  it('produces a distinct id per call', () => {
    expect(newRequestId()).not.toBe(newRequestId());
  });

  it('falls back when the environment has no randomUUID', () => {
    vi.stubGlobal('crypto', {});

    expect(newRequestId()).toMatch(/^req-/);
  });
});
