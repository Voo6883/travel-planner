import { vi, type Mock } from 'vitest';
import type { paths } from '@/generated/api/schema';

/**
 * Contract-shaped HTTP mocking, keyed on the **generated** path union.
 *
 * The brief allows "Mock Service Worker or equivalent generated-contract mocks". This is the
 * equivalent, and it buys something MSW does not: the handler map is typed as
 * `Partial<Record<keyof paths, …>>`, so a stub for a path the contract does not publish — or for
 * one renamed by a later `npm run codegen` — is a **compile error**. An MSW handler string would
 * keep matching nothing and the test would keep passing against a URL that no longer exists.
 *
 * It intercepts `fetch` rather than the network because `lib/api/client.ts` is the only thing in
 * the app that calls `fetch`; stubbing it exercises the real base URL, credentials, CSRF, and
 * error-translation code paths instead of bypassing them.
 */

export type ContractPath = keyof paths;

export interface StubbedResponse {
  status?: number;
  body?: unknown;
  requestId?: string;
}

export type ContractHandlers = Partial<Record<ContractPath, StubbedResponse>>;

export interface RecordedCall {
  url: string;
  method: string;
  headers: Record<string, string>;
  credentials: RequestCredentials | undefined;
  body: string | undefined;
}

export interface ContractMock {
  fetchMock: Mock;
  calls: RecordedCall[];
  /** The single call made so far. Throws rather than returning undefined on a silent no-call. */
  lastCall: () => RecordedCall;
}

export function mockContract(handlers: ContractHandlers): ContractMock {
  const calls: RecordedCall[] = [];

  const fetchMock = vi.fn((url: string, init: RequestInit = {}) => {
    calls.push(recordCall(url, init));
    const stub = matchHandler(url, handlers);
    const status = stub?.status ?? 200;
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      headers: new Headers(stub?.requestId ? { 'X-Request-Id': stub.requestId } : {}),
      json: () => Promise.resolve(stub?.body),
    } as Response);
  });

  vi.stubGlobal('fetch', fetchMock);

  return {
    fetchMock,
    calls,
    lastCall: () => {
      const call = calls.at(-1);
      if (call === undefined) {
        throw new Error('No request was made.');
      }
      return call;
    },
  };
}

function recordCall(url: string, init: RequestInit): RecordedCall {
  return {
    url,
    method: init.method ?? 'GET',
    headers: (init.headers ?? {}) as Record<string, string>,
    credentials: init.credentials,
    body: typeof init.body === 'string' ? init.body : undefined,
  };
}

/**
 * Longest match wins, so `/auth/me` cannot be answered by a `/auth` stub.
 *
 * Templated segments are matched structurally: a stub for `/auth/providers/{provider}` answers
 * `/api/v1/auth/providers/FIREBASE_GOOGLE`. Keeping the key in its contract form is what lets the
 * handler map stay typed on `keyof paths` — a concrete path would not be a member of that union
 * and would force the type open.
 */
function matchHandler(url: string, handlers: ContractHandlers): StubbedResponse | undefined {
  const pathOnly = url.split('?')[0] ?? url;
  const match = Object.keys(handlers)
    .filter((path) => pathPattern(path).test(pathOnly))
    .sort((left, right) => right.length - left.length)
    .at(0);

  return match === undefined ? undefined : handlers[match as ContractPath];
}

function pathPattern(contractPath: string): RegExp {
  const escaped = contractPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  // `\{param\}` after escaping — one path segment, no slashes.
  return new RegExp(`${escaped.replace(/\\\{\w+\\\}/g, '[^/]+')}$`);
}
