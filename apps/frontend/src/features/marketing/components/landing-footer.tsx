import { getTranslations } from 'next-intl/server';
import { SystemStatusCard } from '@/components/ui/system-status-card';

/** Footer with scaffold notice and system status for the marketing landing. */
export async function LandingFooter() {
  const t = await getTranslations('common');

  return (
    <footer className="border-t border-border-subtle">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-8 md:px-6 xl:px-8">
        <p className="m-0 rounded-lg border border-selection-border bg-selection-surface px-4 py-3 text-body-sm text-selection-text">
          {t('scaffold_notice')}
        </p>
        <SystemStatusCard />
      </div>
    </footer>
  );
}
