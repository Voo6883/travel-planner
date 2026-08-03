import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { PlannerHomePanel } from '@/features/chat';
import { TripListPanel } from '@/features/intake';

/**
 * `/trips` — authenticated landing (PLAN §3.2, design system §8.3).
 *
 * Chat is the primary first region; the trip list is the returning-user / manual fallback surface
 * (UC-T01b). Route stays composition-only.
 */
export default async function TripsPage() {
  const t = await getTranslations('chat');

  return (
    <PageHeader title={t('panel.planner_title')} description={t('home.intro')}>
      <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_minmax(280px,360px)]">
        <PlannerHomePanel />
        <TripListPanel />
      </div>
    </PageHeader>
  );
}
