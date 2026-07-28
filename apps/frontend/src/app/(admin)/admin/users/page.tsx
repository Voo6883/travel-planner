import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { UserListPanel } from '@/features/admin';

/**
 * `/admin/users` — UC-A15.
 *
 * Thin route composition only (PLAN §4.2: pages live in `app/`, everything else in `features/`).
 * The guard and the shell come from the `(admin)` layout task 11 shipped; this adds the header
 * §8.10 asks for and the panel that owns the data.
 */
export default async function AdminUsersPage() {
  const t = await getTranslations('admin');

  return (
    <PageHeader title={t('list.title')} description={t('list.description')}>
      <UserListPanel />
    </PageHeader>
  );
}
