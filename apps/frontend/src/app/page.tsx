import { getTranslations } from 'next-intl/server';
import { PageShell } from '@/components/layout/page-shell';
import { SystemStatusCard } from '@/components/ui/system-status-card';

/**
 * Home / status screen.
 *
 * Composition only — no data fetching, no business logic (PLAN §4.2.2). The planner chat
 * entry point replaces this content in tasks/21.
 */
export default async function HomePage() {
  const t = await getTranslations('common');

  return (
    <PageShell title={t('app_name')} description={t('app_tagline')}>
      <div className="flex flex-col gap-4">
        <p className="rounded-md bg-selection-surface px-3 py-2 text-sm text-selection-text">
          {t('scaffold_notice')}
        </p>
        <SystemStatusCard />
      </div>
    </PageShell>
  );
}
