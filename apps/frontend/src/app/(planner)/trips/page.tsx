import { getTranslations } from 'next-intl/server';
import { PageHeader } from '@/components/layout/page-header';
import { TripListPanel } from '@/features/intake';

/**
 * `/trips` — the authenticated landing, and where every completed sign-in arrives.
 *
 * Thin route composition only (PLAN §4.2). Task 21 adds the chat-first composer beside this list;
 * task 18 owns the manual fallback and user-scoped trip navigation.
 */
export default async function TripsPage() {
  const t = await getTranslations('trip_brief');

  return (
    <PageHeader title={t('list.page_title')} description={t('list.page_description')}>
      <TripListPanel />
    </PageHeader>
  );
}
