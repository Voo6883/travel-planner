import { getRequestConfig } from 'next-intl/server';
import { cookies } from 'next/headers';
import { defaultLocale, isSupportedLocale, localeCookieName } from './config';

/**
 * next-intl request configuration.
 *
 * Namespaces are loaded explicitly rather than by globbing so that a missing translation file
 * is a build-time failure instead of a blank label at runtime. Feature namespaces
 * (trip_brief, research, itinerary, booking_flow, chat) are added by their owning tasks.
 */
export default getRequestConfig(async () => {
  const cookieStore = await cookies();
  const requested = cookieStore.get(localeCookieName)?.value;
  const locale = isSupportedLocale(requested) ? requested : defaultLocale;

  const common = (await import(`../../locales/${locale}/common.json`)) as {
    default: Record<string, string>;
  };

  return {
    locale,
    messages: {
      common: common.default,
    },
  };
});
