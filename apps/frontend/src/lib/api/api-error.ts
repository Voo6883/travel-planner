import type { components } from '@/generated/api/schema';

/**
 * The error envelope, end to end. Every type here is derived from the generated contract, so a
 * change to `errors.yaml` shows up as a TypeScript error rather than as a runtime surprise.
 */
export type ErrorCode = components['schemas']['ErrorCode'];
export type ApiErrorResponse = components['schemas']['ApiErrorResponse'];
export type ValidationFailedDetails = components['schemas']['ValidationFailedDetails'];

/**
 * Runtime copy of the registered codes. TypeScript enums exist only at compile time, so a value
 * list is needed to narrow an arbitrary server string.
 */
export const REGISTERED_ERROR_CODES = [
  'account_disabled',
  'account_locked',
  'email_not_verified',
  'firebase_email_not_verified',
  'forbidden',
  'identity_already_linked',
  'internal_error',
  'invalid_credentials',
  'invalid_firebase_token',
  'invalid_oauth_state',
  'invalid_token',
  'last_sign_in_method',
  'not_found',
  'provider_email_unavailable',
  'provider_link_required',
  'provider_unavailable',
  'rate_limited',
  'unauthorized',
  'validation_failed',
  'version_conflict',
] as const satisfies readonly ErrorCode[];

/**
 * Compile-time guard in the other direction: `satisfies` above proves every listed value is a real
 * code, and this proves every real code is listed. Add a code to `errors.yaml`, regenerate, forget
 * this list — and `npm run typecheck` fails instead of the UI quietly showing a raw identifier.
 */
type MissingErrorCodes = Exclude<ErrorCode, (typeof REGISTERED_ERROR_CODES)[number]>;
export const ERROR_CODE_LIST_IS_EXHAUSTIVE: MissingErrorCodes extends never ? true : never = true;

export function isErrorCode(value: unknown): value is ErrorCode {
  return typeof value === 'string' && (REGISTERED_ERROR_CODES as readonly string[]).includes(value);
}

/**
 * PLAN §6.1: the frontend maps `code` to `common.errors.<code>` and never displays `message`,
 * which is an English developer string. An unregistered code falls back rather than rendering a
 * raw key — a user should never see `errors.some_new_code` in the interface.
 */
export function i18nKeyForErrorCode(code: string): string {
  return isErrorCode(code) ? `errors.${code}` : 'errors.internal_error';
}

interface ApiErrorInit {
  status: number;
  code: string;
  message: string;
  details?: Record<string, unknown> | null;
  requestId?: string | null;
}

/** Typed failure thrown by every function in `lib/api/`. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown>;
  readonly requestId: string | null;

  constructor(init: ApiErrorInit) {
    super(init.message);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.details = init.details ?? {};
    this.requestId = init.requestId ?? null;
  }

  /** The translation key the UI should render. Never show `message` to a user. */
  get i18nKey(): string {
    return i18nKeyForErrorCode(this.code);
  }

  /**
   * Server version behind a `409 version_conflict` (ADR 008). The conflict UX re-reads at this
   * version, re-applies the user's uncommitted edits, and retries — it never clobbers.
   */
  get currentVersion(): number | null {
    const current = this.details['current_version'];
    return typeof current === 'number' ? current : null;
  }

  /**
   * Seconds to wait behind a `423 account_locked` (ADR 009 §6), so the sign-in form can show a
   * countdown rather than inviting an attempt that is guaranteed to fail.
   */
  get retryAfterSeconds(): number | null {
    const retryAfter = this.details['retry_after_seconds'];
    return typeof retryAfter === 'number' ? retryAfter : null;
  }

  /**
   * Which provider a `409 provider_link_required` is about (ADR 009 §4).
   *
   * The refusal is not a dead end — the recovery is "sign in to the account that already holds
   * this address, then connect <provider>" — and naming the provider is what makes that
   * instruction actionable rather than a riddle.
   */
  get linkRequiredProvider(): string | null {
    const provider = this.details['provider'];
    return typeof provider === 'string' ? provider : null;
  }

  /** Field errors behind a `400 validation_failed`, for form-level highlighting. */
  get fieldErrors(): Record<string, string> {
    const fields = this.details['fields'];
    if (typeof fields !== 'object' || fields === null) {
      return {};
    }
    return Object.fromEntries(
      Object.entries(fields as Record<string, unknown>).filter(
        (entry): entry is [string, string] => typeof entry[1] === 'string',
      ),
    );
  }
}

/**
 * Builds an `ApiError` from a non-2xx response.
 *
 * A failing response is not guaranteed to carry the envelope — a proxy 502 or a gateway timeout
 * returns HTML — so the envelope is parsed defensively and anything unrecognised becomes
 * `internal_error`. Trusting the body here would let a proxy decide what the UI says.
 */
export async function apiErrorFromResponse(response: Response): Promise<ApiError> {
  const requestId = response.headers?.get('X-Request-Id') ?? null;
  const envelope = await readEnvelope(response);

  return new ApiError({
    status: response.status,
    code: isErrorCode(envelope?.code) ? envelope.code : 'internal_error',
    message: typeof envelope?.message === 'string' ? envelope.message : 'Request failed.',
    details: (envelope?.details ?? {}) as Record<string, unknown>,
    requestId,
  });
}

async function readEnvelope(response: Response): Promise<ApiErrorResponse | null> {
  try {
    const payload: unknown = await response.json();
    return typeof payload === 'object' && payload !== null ? (payload as ApiErrorResponse) : null;
  } catch {
    return null;
  }
}
