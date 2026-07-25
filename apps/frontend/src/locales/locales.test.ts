import { describe, expect, it } from 'vitest';
import { locales } from '@/lib/i18n/config';
import en from './en/common.json';
import ms from './ms/common.json';

/**
 * Guards the i18n contract (PLAN §4.2.10). A missing key renders a raw key string in the UI, so
 * divergence between locales is caught here rather than in review.
 */
describe('locale message files', () => {
  it('declares exactly the locales that have message folders', () => {
    expect([...locales]).toEqual(['en', 'ms']);
  });

  it('ms defines every key that en defines, and no extras', () => {
    expect(Object.keys(ms).sort()).toEqual(Object.keys(en).sort());
  });

  it('uses snake_case keys only', () => {
    for (const key of Object.keys(en)) {
      expect(key).toMatch(/^[a-z][a-z0-9_]*$/);
    }
  });

  it('has no empty translations', () => {
    for (const [key, value] of Object.entries({ ...en, ...ms })) {
      expect(value, `empty value for ${key}`).not.toBe('');
    }
  });
});
