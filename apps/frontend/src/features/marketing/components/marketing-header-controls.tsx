'use client';

import { Select } from 'antd';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { locales, localeCookieName, type Locale } from '@/lib/i18n/config';

/** Compact locale selector for the marketing header. */
export function MarketingHeaderControls() {
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
      className="min-w-[9rem]"
      options={locales.map((locale) => ({ value: locale, label: t(`locale_${locale}`) }))}
    />
  );
}
