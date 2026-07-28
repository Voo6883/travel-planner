import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { AccountSettingsPanel } from '@/features/auth';

/** `/settings` — UC-A11, UC-A12, UC-A14 and the §8.9 account sections. */
export default async function SettingsPage() {
  const t = await getTranslations('auth');

  return (
    <PageHeader title={t('settings_title')} description={t('settings_subtitle')}>
      <AccountSettingsPanel />
    </PageHeader>
  );
}
