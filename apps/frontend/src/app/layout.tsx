import type { Metadata, Viewport } from 'next';
import { getLocale, getMessages, getTranslations } from 'next-intl/server';
import type { ReactNode } from 'react';
import '@ant-design/v5-patch-for-react-19';
import { AppProviders } from '@/components/layout/app-providers';
import { ServiceWorkerUpdatePrompt } from '@/components/ui/service-worker-update-prompt';
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
  /**
   * Browser UI colour once installed (design system §9.2). Unlike the manifest's `theme_color`,
   * which the OS reads once at install time, this meta tag is re-evaluated per colour scheme —
   * so the address bar and status bar follow the app's own light/dark switch instead of staying
   * on the light-mode brand blue while the page is dark.
   *
   * The values are the `canvas` token for each mode, mirrored from `design-tokens.ts`. They are
   * intentionally the surface behind the header rather than the brand blue: a status bar tinted
   * to match the page is what makes a standalone window look native (§9.2 "splash: solid theme
   * background"), and it cannot fight the no-flash bootstrap script below because both resolve
   * from the same preference.
   */
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#F8FAFC' },
    { media: '(prefers-color-scheme: dark)', color: '#0B1220' },
  ],
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
          {/* After the skip link so it never steals the first tab stop, before the page so the
              notice is not buried below the fold (design system §9.4). */}
          <ServiceWorkerUpdatePrompt />
          {children}
        </AppProviders>
      </body>
    </html>
  );
}
