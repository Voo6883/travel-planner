import { BookOutlined, CheckCircleOutlined, LockOutlined } from '@ant-design/icons';
import { getTranslations } from 'next-intl/server';

const trustItems = [
  { key: 'sources', icon: BookOutlined },
  { key: 'booking', icon: CheckCircleOutlined },
  { key: 'account', icon: LockOutlined },
] as const;

/** Trust section: grounded sources, human-confirmed booking, secure account (§8.1). */
export async function LandingTrust() {
  const t = await getTranslations('marketing');

  return (
    <section
      aria-labelledby="landing-trust-heading"
      className="border-t border-border-subtle bg-surface-subtle"
    >
      <div className="mx-auto w-full max-w-7xl px-4 py-12 md:px-6 md:py-16 xl:px-8">
        <div className="mb-10 max-w-prose">
          <h2 id="landing-trust-heading" className="m-0 text-h2 text-foreground">
            {t('trust_title')}
          </h2>
          <p className="mb-0 mt-2 text-body text-foreground-muted">{t('trust_subtitle')}</p>
        </div>

        <ul className="m-0 grid list-none gap-6 p-0 md:grid-cols-3">
          {trustItems.map(({ key, icon: Icon }) => (
            <li
              key={key}
              className="flex flex-col gap-3 rounded-lg border border-border-subtle bg-surface p-6"
            >
              <span
                aria-hidden
                className="flex size-10 items-center justify-center rounded-lg bg-ai-accent-surface text-ai-accent-text"
              >
                <Icon className="text-xl" />
              </span>
              <div>
                <h3 className="m-0 text-title text-foreground">
                  {t(`trust_${key}_title`)}
                </h3>
                <p className="mb-0 mt-2 text-body-sm text-foreground-muted">
                  {t(`trust_${key}_body`)}
                </p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
