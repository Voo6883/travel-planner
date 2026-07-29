import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { LandingPreview } from '@/features/marketing/components/landing-preview';

/** Hero region (§8.1): value proposition, chat-first message, primary sign-up CTA. */
export async function LandingHero() {
  const t = await getTranslations('marketing');

  return (
    <section aria-labelledby="landing-hero-heading" className="border-b border-border-subtle bg-surface">
      <div className="mx-auto grid w-full max-w-7xl gap-10 px-4 py-12 md:grid-cols-2 md:items-center md:gap-12 md:px-6 md:py-16 xl:px-8">
        <div className="order-1 flex flex-col gap-6">
          <div className="flex flex-col gap-4">
            <h1 id="landing-hero-heading" className="m-0 text-h1-mobile text-foreground sm:text-display">
              {t('hero_title')}
            </h1>
            <p className="m-0 max-w-prose text-body text-foreground-muted">{t('hero_subtitle')}</p>
          </div>

          <div className="flex flex-wrap gap-3">
            <Link
              href="/sign-up"
              className="inline-flex min-h-control items-center rounded-md bg-action-primary-fill px-5 text-body-sm font-medium text-white no-underline transition-colors duration-color ease-out hover:bg-action-primary-fill-hover"
            >
              {t('cta_sign_up')}
            </Link>
            <Link
              href="/sign-in"
              className="inline-flex min-h-control items-center rounded-md border border-border px-5 text-body-sm font-medium text-foreground no-underline transition-colors duration-color ease-out hover:bg-surface-subtle"
            >
              {t('cta_sign_in')}
            </Link>
          </div>
        </div>

        <div className="order-2 md:order-2">
          <LandingPreview />
        </div>
      </div>
    </section>
  );
}
