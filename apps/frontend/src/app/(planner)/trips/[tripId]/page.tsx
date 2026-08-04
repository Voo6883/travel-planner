import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { TripChatPanel } from '@/features/chat';
import { TripBriefEditor } from '@/features/intake';
import { ResearchPanel } from '@/features/research';

interface TripDetailPageProps {
  params: Promise<{ tripId: string }>;
}

/**
 * `/trips/{tripId}` — C1 brief + continuing trip chat after `trip_created` handoff.
 */
export default async function TripDetailPage({ params }: TripDetailPageProps) {
  const [{ tripId }, t] = await Promise.all([params, getTranslations('trip_brief')]);

  return (
    <PageHeader title={t('detail.page_title')} description={t('detail.page_description')}>
      <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_minmax(280px,360px)]">
        <div className="flex flex-col gap-8">
          <TripBriefEditor tripId={tripId} />
          <ResearchPanel tripId={tripId} />
        </div>
        <TripChatPanel tripId={tripId} />
      </div>
    </PageHeader>
  );
}
