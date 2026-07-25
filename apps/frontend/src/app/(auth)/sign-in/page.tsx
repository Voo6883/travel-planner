import { getTranslations } from 'next-intl/server';
import { PageShell } from '@/components/layout/page-shell';

/**
 * Placeholder for the `(auth)` route group.
 *
 * Exists so the route boundary is real and reviewable; it holds no authentication logic.
 * Local login, Firebase Google, and GitHub OAuth are built in tasks/08, 10, and 11.
 */
export default async function SignInPlaceholderPage() {
  const t = await getTranslations('common');

  return (
    <PageShell title={t('app_name')}>
      <p className="text-sm text-foreground-muted">{t('scaffold_notice')}</p>
    </PageShell>
  );
}
