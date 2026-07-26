import { describe, expect, it } from 'vitest';
import { locales } from '@/lib/i18n/config';
import { REGISTERED_ERROR_CODES } from '@/lib/api/api-error';
import en from './en/common.json';
import ms from './ms/common.json';

/**
 * Guards the i18n contract (PLAN §4.2.10). A missing key renders a raw key string in the UI, so
 * divergence between locales is caught here rather than in review.
 *
 * Flattened rather than compared shallowly: `errors.*` is nested, and a top-level comparison would
 * pass while an entire namespace was missing from one locale.
 */
type Messages = Record<string, unknown>;

function flatten(messages: Messages, prefix = ''): Record<string, string> {
  const flat: Record<string, string> = {};
  for (const [key, value] of Object.entries(messages)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'object' && value !== null) {
      Object.assign(flat, flatten(value as Messages, path));
    } else {
      flat[path] = String(value);
    }
  }
  return flat;
}

const flatEn = flatten(en);
const flatMs = flatten(ms);

describe('locale message files', () => {
  it('declares exactly the locales that have message folders', () => {
    expect([...locales]).toEqual(['en', 'ms']);
  });

  it('ms defines every key that en defines, and no extras', () => {
    expect(Object.keys(flatMs).sort()).toEqual(Object.keys(flatEn).sort());
  });

  it('uses snake_case keys only, at every depth', () => {
    for (const key of Object.keys(flatEn)) {
      for (const segment of key.split('.')) {
        expect(segment, `key ${key}`).toMatch(/^[a-z][a-z0-9_]*$/);
      }
    }
  });

  it('has no empty translations', () => {
    for (const [key, value] of Object.entries({ ...flatEn, ...flatMs })) {
      expect(value, `empty value for ${key}`).not.toBe('');
    }
  });
});

/**
 * The frontend half of the error-code registration procedure documented at the top of
 * `api/openapi/errors.yaml`. The backend half — spec enum versus `ApiErrorCode` — is asserted by
 * `ErrorCatalogTest`. Together they mean a code cannot be registered in one place only.
 */
describe('error code translations', () => {
  it('every registered code has a message in every locale', () => {
    for (const code of REGISTERED_ERROR_CODES) {
      expect(flatEn[`errors.${code}`], `en errors.${code}`).toBeTruthy();
      expect(flatMs[`errors.${code}`], `ms errors.${code}`).toBeTruthy();
    }
  });

  it('defines no translation for a code the contract does not publish', () => {
    const translated = Object.keys(flatEn)
      .filter((key) => key.startsWith('errors.'))
      .map((key) => key.slice('errors.'.length));

    expect(translated.sort()).toEqual([...REGISTERED_ERROR_CODES].sort());
  });
});
