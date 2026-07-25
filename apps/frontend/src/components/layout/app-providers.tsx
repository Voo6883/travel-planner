'use client';

import { AntdRegistry } from '@ant-design/nextjs-registry';
import { ConfigProvider } from 'antd';
import { NextIntlClientProvider, type AbstractIntlMessages } from 'next-intl';
import type { ReactNode } from 'react';
import { antTheme } from '@/styles/ant-theme';

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
 * The React Query provider is added by tasks/06/11 when there is server state to cache —
 * adding it now would be an unused dependency.
 */
export function AppProviders({ locale, messages, children }: AppProvidersProps) {
  return (
    <AntdRegistry>
      <NextIntlClientProvider locale={locale} messages={messages}>
        <ConfigProvider theme={antTheme}>{children}</ConfigProvider>
      </NextIntlClientProvider>
    </AntdRegistry>
  );
}
