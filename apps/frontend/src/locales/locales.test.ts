import { describe, expect, it } from 'vitest';
import { locales } from '@/lib/i18n/config';
import { REGISTERED_ERROR_CODES } from '@/lib/api/api-error';
import enCommon from './en/common.json';
import msCommon from './ms/common.json';
import enAuth from './en/auth.json';
import msAuth from './ms/auth.json';
import enAdmin from './en/admin.json';
import msAdmin from './ms/admin.json';
import enMarketing from './en/marketing.json';
import msMarketing from './ms/marketing.json';

/**
 * Guards the i18n contract (PLAN §4.2.10). A missing key renders a raw key string in the UI, so
 * divergence between locales is caught here rather than in review.
 *
 * Flattened rather than compared shallowly: `errors.*` and `nav.*` are nested, and a top-level
 * comparison would pass while an entire namespace was missing from one locale.
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

/**
 * Every namespace loaded by `lib/i18n/request.ts`. Adding a namespace there without adding it
 * here would leave it unguarded, so the list is deliberately duplicated rather than globbed.
 */
const namespaces = [
  { name: 'common', en: enCommon, ms: msCommon },
  { name: 'auth', en: enAuth, ms: msAuth },
  { name: 'admin', en: enAdmin, ms: msAdmin },
  { name: 'marketing', en: enMarketing, ms: msMarketing },
] as const;

const flatEn = flatten(enCommon);
const flatMs = flatten(msCommon);

describe('locale message files', () => {
  it('declares exactly the locales that have message folders', () => {
    expect([...locales]).toEqual(['en', 'ms']);
  });

  it.each(namespaces)('$name: ms defines every key that en defines, and no extras', (namespace) => {
    expect(Object.keys(flatten(namespace.ms)).sort()).toEqual(Object.keys(flatten(namespace.en)).sort());
  });

  it.each(namespaces)('$name: uses snake_case keys only, at every depth', (namespace) => {
    for (const key of Object.keys(flatten(namespace.en))) {
      for (const segment of key.split('.')) {
        expect(segment, `key ${key}`).toMatch(/^[a-z][a-z0-9_]*$/);
      }
    }
  });

  it.each(namespaces)('$name: has no empty translations', (namespace) => {
    const all = { ...flatten(namespace.en), ...flatten(namespace.ms) };
    for (const [key, value] of Object.entries(all)) {
      expect(value, `empty value for ${key}`).not.toBe('');
    }
  });

  /**
   * A placeholder that exists in one locale and not the other renders the literal `{email}` to
   * half the users. ICU arguments are part of the contract between the two files, not decoration.
   */
  it.each(namespaces)('$name: uses the same ICU placeholders in both locales', (namespace) => {
    const en = flatten(namespace.en);
    const ms = flatten(namespace.ms);
    for (const [key, value] of Object.entries(en)) {
      expect(placeholders(ms[key] ?? ''), `placeholders for ${key}`).toEqual(placeholders(value));
    }
  });
});

function placeholders(value: string): string[] {
  return [...value.matchAll(/\{(\w+)\}/g)].map((match) => match[1] ?? '').sort();
}

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
