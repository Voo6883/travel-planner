import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { EmptyState } from '@/components/ui/empty-state';

/**
 * `/admin` — now genuinely guarded (task 11), but still empty.
 *
 * User administration, audit views, and password reset are task 12. The route exists here only so
 * the `ADMIN` role check is real and reviewable rather than a promise.
 */
export default async function AdminPage() {
  const t = await getTranslations('common');

  return (
    <PageHeader title={t('nav.admin')}>
      <EmptyState title={t('states.empty_title')} description={t('scaffold_notice')} />
    </PageHeader>
  );
}
