import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { UserDetailPanel } from '@/features/admin';

interface AdminUserPageProps {
  params: Promise<{ userId: string }>;
}

/**
 * `/admin/users/{userId}` — UC-A15 and UC-A16.
 *
 * The id is passed straight through to the panel and never used to fetch here: authorisation is
 * the server's decision on every request, and rendering anything about the account before that
 * decision has been made would be this page asserting something it cannot know.
 */
export default async function AdminUserDetailPage({ params }: AdminUserPageProps) {
  const [{ userId }, t] = await Promise.all([params, getTranslations('admin')]);

  return (
    <PageHeader title={t('detail.page_title')} description={t('detail.description')}>
      <UserDetailPanel userId={userId} />
    </PageHeader>
  );
}
