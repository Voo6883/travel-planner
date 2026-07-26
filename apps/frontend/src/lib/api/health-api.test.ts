import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchBackendStatus, fetchHealth, fetchReadiness } from './health-api';

function stubJson(body: unknown, status = 200) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      headers: new Headers(),
      json: async () => body,
    }),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('health api', () => {
  it('validates the health payload at the boundary', async () => {
    stubJson({ status: 'UP' });

    await expect(fetchHealth()).resolves.toEqual({ status: 'UP' });
  });

  it('rejects a payload the contract does not allow', async () => {
    // Types describe the promise; the schema checks what actually arrived.
    stubJson({ status: 'MAYBE' });

    await expect(fetchHealth()).rejects.toThrow();
  });

  it('returns the per-component readiness breakdown', async () => {
    stubJson({ status: 'DOWN', components: { database: 'DOWN: unreachable' } });

    await expect(fetchReadiness()).resolves.toEqual({
      status: 'DOWN',
      components: { database: 'DOWN: unreachable' },
    });
  });

  it('reports unreachable instead of throwing, so the shell renders without a backend', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNREFUSED')));

    await expect(fetchBackendStatus()).resolves.toBe('unreachable');
  });

  it('treats a malformed payload as unreachable rather than trusting it', async () => {
    stubJson({ unexpected: true });

    await expect(fetchBackendStatus()).resolves.toBe('unreachable');
  });

  it('reports ready when the backend answers UP', async () => {
    stubJson({ status: 'UP' });

    await expect(fetchBackendStatus()).resolves.toBe('ready');
  });
});
