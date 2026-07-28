'use client';

import { AntdRegistry } from '@ant-design/nextjs-registry';
import { QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import enUS from 'antd/locale/en_US';
import msMY from 'antd/locale/ms_MY';
import { NextIntlClientProvider, type AbstractIntlMessages } from 'next-intl';
import { useState, type ReactNode } from 'react';
import { ThemeModeProvider, useThemeMode } from '@/hooks/use-theme-mode';
import { createQueryClient } from '@/lib/query/client';
import { buildAntTheme } from '@/styles/ant-theme';

interface AppProvidersProps {
  locale: string;
  // next-intl's own message type — a looser Record<string, unknown> does not satisfy it and
  // would force a cast at the one place type safety actually matters.
  messages: AbstractIntlMessages;
  children: ReactNode;
}

/**
 * Client-side provider stack.
 *
 * AntdRegistry collects Ant's CSS-in-JS during SSR so the first paint is not unstyled.
 * `ThemeModeProvider` sits outside `ConfigProvider` because the Ant theme is derived from the
 * resolved mode — §12.2 requires Tailwind and Ant to switch off one shared state, not two.
 */
export function AppProviders({ locale, messages, children }: AppProvidersProps) {
  // Lazy initialiser, not a module-level client: on the server one module instance is shared by
  // every request, so a module-level cache would leak one user's `/auth/me` into another's render.
  const [queryClient] = useState(createQueryClient);

  return (
    /**
     * Default `hashPriority` ("low") is kept deliberately. Ant Design 5 emits
     * `:where(.css-hash).ant-btn`, whose low specificity is exactly what lets a Tailwind utility
     * on a `className` prop win — PLAN §4.2.9's "Tailwind overrides Ant where they collide".
     * Raising it to "high" would invert that for every Ant component whose styles are injected
     * after hydration, which is the opposite of the locked decision.
     *
     * The consequence is that Tailwind's Preflight (`button, input { padding: 0 }`,
     * `* { border-width: 0 }`) sits close to Ant's control geometry in the cascade, so
     * `globals.css` states the 44/48 px control height explicitly rather than inheriting it —
     * see the `@layer components` block there.
     */
    <AntdRegistry>
      <NextIntlClientProvider locale={locale} messages={messages}>
        <QueryClientProvider client={queryClient}>
          <ThemeModeProvider>
            <ThemedAntProvider locale={locale}>{children}</ThemedAntProvider>
          </ThemeModeProvider>
        </QueryClientProvider>
      </NextIntlClientProvider>
    </AntdRegistry>
  );
}

interface ThemedAntProviderProps {
  locale: string;
  children: ReactNode;
}

/**
 * Split out only because `useThemeMode()` cannot be called in the component that renders the
 * provider it reads from.
 *
 * `AntApp` supplies the `message`/`notification`/`modal` instances that `App.useApp()` returns.
 * Without it those APIs fall back to a static call that never sees `ConfigProvider`, so toasts
 * would render in default Ant blue while the rest of the app is themed (PLAN §4.2.6-K2).
 */
function ThemedAntProvider({ locale, children }: ThemedAntProviderProps) {
  const { mode } = useThemeMode();

  return (
    <ConfigProvider theme={buildAntTheme(mode)} locale={locale === 'ms' ? msMY : enUS}>
      <AntApp className="contents">{children}</AntApp>
    </ConfigProvider>
  );
}
