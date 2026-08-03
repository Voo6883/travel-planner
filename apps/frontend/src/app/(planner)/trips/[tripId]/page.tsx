import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { TripBriefEditor } from '@/features/intake';

interface TripDetailPageProps {
  params: Promise<{ tripId: string }>;
}

/**
 * `/trips/{tripId}` — C1 structured brief editor.
 *
 * The route passes the id through without fetching. Ownership, archived read-only state, and the
 * latest brief version all come from the API calls inside the feature panel.
 */
export default async function TripDetailPage({ params }: TripDetailPageProps) {
  const [{ tripId }, t] = await Promise.all([params, getTranslations('trip_brief')]);

  return (
    <PageHeader title={t('detail.page_title')} description={t('detail.page_description')}>
      <TripBriefEditor tripId={tripId} />
    </PageHeader>
  );
}
