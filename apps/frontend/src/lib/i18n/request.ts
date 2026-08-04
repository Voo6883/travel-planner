import type { AbstractIntlMessages } from 'next-intl';
import { getRequestConfig } from 'next-intl/server';
import { cookies } from 'next/headers';
import { defaultLocale, isSupportedLocale, localeCookieName } from './config';

/**
 * next-intl request configuration.
 *
 * Namespaces are loaded explicitly rather than by globbing so that a missing translation file
 * is a build-time failure instead of a blank label at runtime. Feature namespaces
 * (trip_brief, research, itinerary, booking_flow, chat) are added by their owning tasks.
 *
 * Namespace `research` (C2) is loaded here so the trip detail screen's research panel resolves.
 */
export default getRequestConfig(async () => {
  const cookieStore = await cookies();
  const requested = cookieStore.get(localeCookieName)?.value;
  const locale = isSupportedLocale(requested) ? requested : defaultLocale;

  const [common, auth, admin, marketing, chat, tripBrief, research] = await Promise.all([
    loadNamespace(locale, 'common'),
    loadNamespace(locale, 'auth'),
    loadNamespace(locale, 'admin'),
    loadNamespace(locale, 'marketing'),
    loadNamespace(locale, 'chat'),
    loadNamespace(locale, 'trip_brief'),
    loadNamespace(locale, 'research'),
  ]);

  return {
    locale,
    messages: { common, auth, admin, marketing, chat, trip_brief: tripBrief, research },
  };
});

/** One namespace file, typed as next-intl's own nested message shape. */
async function loadNamespace(locale: string, namespace: string): Promise<AbstractIntlMessages> {
  const loaded = (await import(`../../locales/${locale}/${namespace}.json`)) as {
    default: AbstractIntlMessages;
  };
  return loaded.default;
}
