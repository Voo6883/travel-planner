import { CompassOutlined, MessageOutlined, RocketOutlined } from '@ant-design/icons';
import { getTranslations } from 'next-intl/server';

const steps = [
  { key: 'describe', icon: MessageOutlined },
  { key: 'discover', icon: CompassOutlined },
  { key: 'go', icon: RocketOutlined },
] as const;

/** Three-step explainer: Describe, Discover, Go (§8.1). */
export async function LandingSteps() {
  const t = await getTranslations('marketing');

  return (
    <section
      aria-labelledby="landing-steps-heading"
      className="mx-auto w-full max-w-7xl px-4 py-12 md:px-6 md:py-16 xl:px-8"
    >
      <div className="mb-10 max-w-prose">
        <h2 id="landing-steps-heading" className="m-0 text-h2 text-foreground">
          {t('steps_title')}
        </h2>
        <p className="mb-0 mt-2 text-body text-foreground-muted">{t('steps_subtitle')}</p>
      </div>

      <ol className="m-0 grid list-none gap-6 p-0 sm:grid-cols-3">
        {steps.map(({ key, icon: Icon }, index) => (
          <li
            key={key}
            className="flex flex-col gap-4 rounded-lg border border-border-subtle bg-surface p-6 shadow-sm"
          >
            <div className="flex items-center gap-3">
              <span
                aria-hidden
                className="flex size-10 items-center justify-center rounded-lg bg-selection-surface text-action-primary-text"
              >
                <Icon className="text-xl" />
              </span>
              <span className="text-caption text-foreground-subtle tabular-nums">
                {String(index + 1).padStart(2, '0')}
              </span>
            </div>
            <div>
              <h3 className="m-0 text-h3 text-foreground">
                {t(`step_${key}_title`)}
              </h3>
              <p className="mb-0 mt-2 text-body-sm text-foreground-muted">
                {t(`step_${key}_body`)}
              </p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
