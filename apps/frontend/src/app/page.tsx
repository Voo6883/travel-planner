import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { PageShell } from '@/components/layout/page-shell';
import { SystemStatusCard } from '@/components/ui/system-status-card';

/**
 * Marketing landing (§8.1) — the one route that needs no session.
 *
 * Composition only: no data fetching, no business logic (PLAN §4.2.2). The full hero, the
 * three-step explainer, and the trust section land with the product screens; what this task adds
 * is the sign-up call to action §8.1 puts first, so an anonymous visitor has a way in.
 */
export default async function HomePage() {
  const t = await getTranslations('common');
  const tAuth = await getTranslations('auth');

  return (
    <PageShell title={t('app_name')} description={t('app_tagline')}>
      <div className="flex flex-wrap gap-3">
        {/* One primary action per region (§6.1); signing in is the quieter sibling. */}
        <Link
          href="/sign-up"
          className="inline-flex min-h-control items-center rounded-md bg-action-primary-fill px-4 text-body-sm font-medium text-white no-underline hover:bg-action-primary-fill-hover"
        >
          {tAuth('sign_up_submit')}
        </Link>
        <Link
          href="/sign-in"
          className="inline-flex min-h-control items-center rounded-md border border-border px-4 text-body-sm font-medium text-foreground no-underline hover:bg-surface-subtle"
        >
          {t('actions.sign_in')}
        </Link>
      </div>

      <p className="m-0 rounded-md bg-selection-surface px-3 py-2 text-body-sm text-selection-text">
        {t('scaffold_notice')}
      </p>

      <SystemStatusCard />
    </PageShell>
  );
}
