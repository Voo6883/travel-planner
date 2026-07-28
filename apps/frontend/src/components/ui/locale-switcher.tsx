'use client';

import { Select } from 'antd';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { locales, localeCookieName, type Locale } from '@/lib/i18n/config';

/**
 * Language selector (§8.9, PLAN §4.2.10).
 *
 * Writes the cookie `lib/i18n/request.ts` reads, then `router.refresh()` re-renders the server
 * components with the new messages. There is no `[locale]` route segment (PLAN §4.2.3 has none),
 * so the cookie *is* the mechanism — and refreshing rather than reloading keeps the user's scroll
 * position and any open form.
 *
 * `SameSite=Lax` and a one-year lifetime: a language preference is not a credential, but it also
 * has no business travelling on a cross-site request.
 *
 * Options are labelled in their own language and never with a flag — §3.5 forbids flags for
 * language, and a flag is a country anyway.
 */
export function LocaleSwitcher() {
  const t = useTranslations('common');
  const router = useRouter();
  const active = useLocale();

  const handleChange = (next: Locale) => {
    document.cookie = `${localeCookieName}=${next}; path=/; max-age=31536000; SameSite=Lax`;
    router.refresh();
  };

  return (
    <Select<Locale>
      aria-label={t('locale_label')}
      value={active as Locale}
      onChange={handleChange}
      className="w-full"
      size="large"
      options={locales.map((locale) => ({ value: locale, label: t(`locale_${locale}`) }))}
    />
  );
}
