import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { MarketingHeaderControls } from '@/features/marketing/components/marketing-header-controls';

/** Top bar for the marketing landing — brand, locale, theme, and sign-in. */
export async function MarketingHeader() {
  const t = await getTranslations('common');
  const tMarketing = await getTranslations('marketing');

  return (
    <header className="sticky top-0 z-40 w-full border-b border-border-subtle bg-surface/95 backdrop-blur-sm">
      <div className="mx-auto flex w-full max-w-7xl items-center justify-between gap-4 px-4 py-3 md:px-6 xl:px-8">
        <Link href="/" className="text-title text-foreground no-underline hover:text-action-primary-text">
          {t('app_name')}
        </Link>

        <div className="flex flex-wrap items-center justify-end gap-3">
          <MarketingHeaderControls />
          <Link
            href="/sign-in"
            className="inline-flex min-h-control items-center rounded-md border border-border px-4 text-body-sm font-medium text-foreground no-underline hover:bg-surface-subtle"
          >
            {tMarketing('cta_sign_in')}
          </Link>
        </div>
      </div>
    </header>
  );
}
