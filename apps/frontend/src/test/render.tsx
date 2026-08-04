import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactElement, ReactNode } from 'react';
import enAdmin from '@/locales/en/admin.json';
import enAuth from '@/locales/en/auth.json';
import enChat from '@/locales/en/chat.json';
import enCommon from '@/locales/en/common.json';
import enResearch from '@/locales/en/research.json';
import enTripBrief from '@/locales/en/trip_brief.json';
import msAdmin from '@/locales/ms/admin.json';
import msAuth from '@/locales/ms/auth.json';
import msChat from '@/locales/ms/chat.json';
import msCommon from '@/locales/ms/common.json';
import msResearch from '@/locales/ms/research.json';
import msTripBrief from '@/locales/ms/trip_brief.json';

export type TestLocale = 'en' | 'ms';

export interface RenderOptions {
  locale?: TestLocale;
}

/**
 * Renders a component inside the same provider stack the app uses.
 *
 * The messages are the **real** locale files, not fixtures. That is the point: a test that passes
 * with invented copy proves nothing about a screen whose labels come from `next-intl`, and using
 * the real files means a missing Malay key fails a component test as well as the locale test.
 *
 * Retries are off and there is no cache carry-over between renders — a shared `QueryClient` would
 * leak one test's `/auth/me` into the next, and retry back-off would turn a deliberate 401 into a
 * timeout.
 */
export function renderWithProviders(ui: ReactElement, options: RenderOptions = {}): RenderResult {
  const locale = options.locale ?? 'en';

  return render(ui, { wrapper: ({ children }) => wrap(children, locale) });
}

export function messagesFor(locale: TestLocale) {
  return locale === 'en'
    ? {
        common: enCommon,
        auth: enAuth,
        admin: enAdmin,
        chat: enChat,
        trip_brief: enTripBrief,
        research: enResearch,
      }
    : {
        common: msCommon,
        auth: msAuth,
        admin: msAdmin,
        chat: msChat,
        trip_brief: msTripBrief,
        research: msResearch,
      };
}

function wrap(children: ReactNode, locale: TestLocale) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });

  return (
    <NextIntlClientProvider locale={locale} messages={messagesFor(locale)}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </NextIntlClientProvider>
  );
}
