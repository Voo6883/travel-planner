/**
 * Locale configuration (PLAN §4.2.10).
 *
 * v1 ships `en` + `ms`. Adding a locale must not require code changes beyond this list and a
 * matching folder under src/locales/ — that is the stated design goal.
 *
 * No `[locale]` route segment is used: the route map in PLAN §4.2.3 has none, so the active
 * locale is resolved server-side from a cookie and defaults to `en`.
 */
export const locales = ['en', 'ms'] as const;

export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = 'en';

/** Cookie the shell reads to pick a locale. */
export const localeCookieName = 'locale';

export function isSupportedLocale(value: string | undefined): value is Locale {
  return value !== undefined && (locales as readonly string[]).includes(value);
}
