import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiRequest, newRequestId } from './client';

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

describe('newRequestId', () => {
  it('produces a distinct id per call', () => {
    expect(newRequestId()).not.toBe(newRequestId());
  });

  it('falls back when the environment has no randomUUID', () => {
    vi.stubGlobal('crypto', {});

    expect(newRequestId()).toMatch(/^req-/);
  });
});
