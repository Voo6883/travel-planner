import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { EmptyState } from '@/components/ui/empty-state';

/**
 * `/trips` — the authenticated landing, and where every completed sign-in arrives.
 *
 * The trip list and the planner chat composer (PLAN §3.2 entry point) belong to tasks 18 and 21;
 * this task owns only the guarded route they will fill. What is real here is the guard, the
 * shell, and the empty state — not placeholder trip data.
 */
export default async function TripsPage() {
  const t = await getTranslations('common');

  return (
    <PageHeader title={t('nav.trips')} description={t('app_tagline')}>
      <EmptyState title={t('states.empty_title')} description={t('scaffold_notice')} />
    </PageHeader>
  );
}
