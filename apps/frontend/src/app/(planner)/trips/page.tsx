import { getTranslations } from 'next-intl/server';
import { PageShell } from '@/components/layout/page-shell';

/**
 * Placeholder for the `(planner)` route group.
 *
 * The trip list plus the planner chat composer (PLAN §3.2 entry point) land in tasks/18 and 21.
 * No trip data, no chat, no stepper here.
 */
export default async function TripsPlaceholderPage() {
  const t = await getTranslations('common');

  return (
    <PageShell title={t('app_name')}>
      <p className="text-sm text-foreground-muted">{t('scaffold_notice')}</p>
    </PageShell>
  );
}
