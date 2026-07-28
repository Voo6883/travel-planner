import type { Metadata, Viewport } from 'next';
import { getLocale, getMessages, getTranslations } from 'next-intl/server';
import type { ReactNode } from 'react';
import '@ant-design/v5-patch-for-react-19';
import { AppProviders } from '@/components/layout/app-providers';
import { THEME_STORAGE_KEY } from '@/hooks/use-theme-mode';
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

/**
 * Resolves the theme class before first paint (§13: "switching theme must not flash the wrong
 * mode during hydration"). It has to be a blocking inline script — a React effect runs after the
 * browser has already painted the server-rendered light theme, which is the flash itself.
 *
 * Deliberately tiny and failure-tolerant: `localStorage` throws in a partitioned iframe or with
 * cookies blocked, and a theme preference is not worth breaking the page over.
 */
const THEME_BOOTSTRAP_SCRIPT = `
try {
  var stored = localStorage.getItem('${THEME_STORAGE_KEY}');
  var dark = stored === 'dark' ||
    ((!stored || stored === 'system') && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.classList.add(dark ? 'dark' : 'light');
} catch (e) {}
`.trim();

export default async function RootLayout({ children }: { children: ReactNode }) {
  const locale = await getLocale();
  const messages = await getMessages();
  const t = await getTranslations('common');

  return (
    <html lang={locale} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_BOOTSTRAP_SCRIPT }} />
      </head>
      <body className="min-h-dvh bg-canvas font-sans text-body text-foreground antialiased">
        <AppProviders locale={locale} messages={messages}>
          <a
            href="#main-content"
            className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-surface focus:px-4 focus:py-2 focus:text-action-primary-text"
          >
            {t('skip_to_content')}
          </a>
          {children}
        </AppProviders>
      </body>
    </html>
  );
}
