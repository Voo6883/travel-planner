import { getTranslations } from 'next-intl/server';
import { PageShell } from '@/components/layout/page-shell';

/**
 * Placeholder for the `(admin)` route group.
 *
 * Deliberately unguarded because there is no auth yet — the role guard and admin screens are
 * built together in tasks/12. Nothing sensitive is reachable from here.
 */
export default async function AdminPlaceholderPage() {
  const t = await getTranslations('common');

  return (
    <PageShell title={t('app_name')}>
      <p className="text-sm text-foreground-muted">{t('scaffold_notice')}</p>
    </PageShell>
  );
}
