import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { OfflineRetryButton } from '@/components/ui/offline-retry-button';

/**
 * Offline fallback shell (PLAN §4.2.3, §4.2.11 — "Offline navigation → Fallback to `/~offline`").
 *
 * The service worker precaches this route and answers any document navigation that cannot reach
 * the network with it. It is reachable directly too, which is what makes it testable and what
 * lets the SW fetch it during install.
 *
 * Deliberately outside `AppShell`: the shell renders navigation to pages that do not work
 * offline, and it calls `useCurrentUser()`, which is an API request that cannot succeed here.
 * Design system §9.3 — "never imply that research, chat, booking, or auto-save completed while
 * offline" — is why the copy states plainly that nothing is stored on the device rather than
 * offering a hopeful "your work is saved locally".
 */
export async function generateMetadata(): Promise<Metadata> {
  // `common` is the namespace `lib/i18n/request.ts` loads; `pwa.*` is a group inside it.
  const t = await getTranslations('common');
  return { title: t('pwa.offline_page_title') };
}

export default async function OfflinePage() {
  const t = await getTranslations('common');

  return (
    <main
      id="main-content"
      className="mx-auto flex min-h-dvh w-full max-w-2xl flex-col justify-center gap-6 px-4 py-10 md:px-6"
    >
      <div>
        {/* `role="status"` on a statically rendered page would announce nothing; the heading is
            the landmark here, and the banner role belongs to `OfflineBanner` inside the app. */}
        <h1 className="m-0 text-h1-mobile text-foreground sm:text-h1">{t('pwa.offline_page_title')}</h1>
        <p className="mb-0 mt-3 max-w-prose text-body text-foreground-muted">
          {t('pwa.offline_page_body')}
        </p>
      </div>

      <dl className="m-0 flex flex-col gap-4">
        <Availability
          term={t('pwa.offline_page_available_title')}
          detail={t('pwa.offline_page_available_body')}
        />
        <Availability
          term={t('pwa.offline_page_unavailable_title')}
          detail={t('pwa.offline_page_unavailable_body')}
        />
      </dl>

      <div>
        <OfflineRetryButton />
      </div>
    </main>
  );
}

function Availability({ term, detail }: { term: string; detail: string }) {
  return (
    <div className="rounded-lg border border-border-subtle bg-surface-subtle p-4">
      <dt className="text-label text-foreground">{term}</dt>
      <dd className="m-0 mt-1 text-body-sm text-foreground-muted">{detail}</dd>
    </div>
  );
}
