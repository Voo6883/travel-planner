import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import enCommon from '@/locales/en/common.json';
import msCommon from '@/locales/ms/common.json';
import { messagesFor, renderWithProviders, type TestLocale } from '@/test/render';

/**
 * The offline shell (PLAN §4.2.3, §4.2.11; design system §9.3).
 *
 * This is the one page a user reaches *because* something failed, so it has to render from the
 * precache with no network, no session, and no API call behind it. Rendering it here in both
 * locales is what proves the copy exists in both — an untranslated offline page is the worst
 * possible place to fall back to a raw message key.
 *
 * `next-intl/server` is mocked because the page is an async server component: `getTranslations`
 * reads the locale cookie through `next/headers`, which has no request context under vitest. The
 * mock is backed by the **real** locale JSON, so a missing Malay key still fails this test.
 */
let activeLocale: TestLocale = 'en';

/**
 * Mirrors `lib/i18n/request.ts` exactly: it loads the namespaces `common` and `auth`, and `pwa.*`
 * is a *group inside* `common`, not a namespace of its own. An earlier version of this mock
 * accepted `getTranslations('pwa')` and resolved it against the raw JSON's top level, which made
 * the test agree with a page that would have rendered raw keys in production. Resolving dotted
 * paths against the real namespace tree is what makes the mock able to fail.
 */
vi.mock('next-intl/server', () => ({
  getTranslations: async (namespace: string) => {
    const namespaces: Record<string, unknown> = {
      common: activeLocale === 'en' ? enCommon : msCommon,
    };
    const root = namespaces[namespace];
    return (key: string) => lookup(root, key) ?? `${namespace}.${key}`;
  },
}));

/** Walks a dotted key path, the way next-intl resolves nested messages. */
function lookup(root: unknown, key: string): string | undefined {
  const value = key.split('.').reduce<unknown>((node, segment) => {
    return typeof node === 'object' && node !== null
      ? (node as Record<string, unknown>)[segment]
      : undefined;
  }, root);
  return typeof value === 'string' ? value : undefined;
}

const { default: OfflinePage, generateMetadata } = await import('./page');

describe.each<TestLocale>(['en', 'ms'])('offline shell (%s)', (locale) => {
  beforeEach(() => {
    activeLocale = locale;
  });

  /** Real locale JSON, so a renamed or dropped key fails here rather than rendering a key path. */
  const copy = () => messagesFor(locale).common.pwa;

  it('renders the offline heading as the page title', async () => {
    renderWithProviders(await OfflinePage(), { locale });

    expect(
      screen.getByRole('heading', { level: 1, name: copy().offline_page_title }),
    ).toBeInTheDocument();
  });

  /**
   * §9.3: "never imply that research, chat, booking, or auto-save completed while offline". The
   * page has to say what does and does not work, or the user is left guessing whether their last
   * edit was saved.
   */
  it('states both what works offline and what needs a connection', async () => {
    renderWithProviders(await OfflinePage(), { locale });

    expect(screen.getByText(copy().offline_page_available_title)).toBeInTheDocument();
    expect(screen.getByText(copy().offline_page_available_body)).toBeInTheDocument();
    expect(screen.getByText(copy().offline_page_unavailable_title)).toBeInTheDocument();
    expect(screen.getByText(copy().offline_page_unavailable_body)).toBeInTheDocument();
  });

  it('offers a retry control', async () => {
    renderWithProviders(await OfflinePage(), { locale });

    expect(screen.getByRole('button', { name: copy().offline_page_retry })).toBeInTheDocument();
  });

  it('titles the document with the offline heading', async () => {
    activeLocale = locale;
    await expect(generateMetadata()).resolves.toMatchObject({
      title: copy().offline_page_title,
    });
  });

  /**
   * The page must not render a raw `common.pwa.*` key. Both the mock and next-intl itself fall
   * back to the key path when a message is missing, so this catches a locale file that lost an
   * entry *and* a component asking for the wrong namespace — which is exactly how the retry
   * button first shipped.
   */
  it('renders no untranslated message keys', async () => {
    const { container } = renderWithProviders(await OfflinePage(), { locale });

    expect(container.textContent).not.toMatch(/(?:common\.)?pwa\.[a-z_]+/);
  });

  /**
   * It owns `<main id="main-content">` itself. The offline page renders outside `AppShell` — it
   * cannot use it, because `AppShell` calls `useCurrentUser()`, an API request that by definition
   * cannot succeed here — so without this the skip link in `layout.tsx` would have no target.
   */
  it('provides the main landmark the skip link targets', async () => {
    renderWithProviders(await OfflinePage(), { locale });

    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content');
  });
});
