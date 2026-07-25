import type { Metadata, Viewport } from 'next';
import { getLocale, getMessages, getTranslations } from 'next-intl/server';
import type { ReactNode } from 'react';
import '@ant-design/v5-patch-for-react-19';
import { AppProviders } from '@/components/layout/app-providers';
import '@/styles/globals.css';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('common');
  return {
    title: t('app_name'),
    description: t('app_tagline'),
  };
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  // No maximum-scale / user-scalable=no: pinch zoom must stay available (a11y).
  viewportFit: 'cover',
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const locale = await getLocale();
  const messages = await getMessages();
  const t = await getTranslations('common');

  return (
    <html lang={locale}>
      <body className="min-h-dvh bg-canvas text-foreground antialiased">
        <AppProviders locale={locale} messages={messages}>
          <a
            href="#main-content"
            className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-surface focus:px-4 focus:py-2 focus:text-action-primary-text"
          >
            {t('skip_to_content')}
          </a>
          {children}
        </AppProviders>
      </body>
    </html>
  );
}
