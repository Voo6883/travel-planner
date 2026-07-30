/**
 * `npm run generate:frontend-feature -- <feature-slug>`
 *
 * A frontend feature is `components/`, `hooks/`, `lib/`, and a barrel — plus two registrations that
 * live outside the directory and are the ones that get missed:
 *
 *   - a **query-key namespace** in `lib/query/query-keys.ts`. PLAN §4.2.6-G forbids inline key
 *     arrays because a typo produces a second cache entry that never invalidates, and nothing fails —
 *     a user just sees stale data, later, on one screen.
 *   - a **locale file per language**. `locales.test.ts` asserts `en` and `ms` have identical key sets,
 *     so a missing Malay file fails the build. A missing *key* inside an existing file fails the same
 *     way, which is why the generator writes both files with the same keys.
 *
 * The barrel matters for a third reason: `eslint.config.mjs` reads the feature list from the directory
 * listing, so creating the folder is what makes the boundary rules apply to it. A feature that reaches
 * into a sibling is an error the moment the directory exists — but only if the directory exists, so a
 * feature assembled loosely under `components/` is a feature with no boundary at all.
 */

import {
  ChangeSet,
  printNextSteps,
  readJson,
  stringifyJson,
  toCamelCase,
  toPascalCase,
  validateSlug,
} from '../generate-support.mjs';

const FRONTEND = 'apps/frontend/src';
const QUERY_KEYS = `${FRONTEND}/lib/query/query-keys.ts`;

export const usage = 'generate:frontend-feature -- <feature-slug>       e.g. itinerary';

export function plan(args) {
  const { slug, error } = validateSlug(args[0]);
  if (error !== null) {
    return { error: `generate:frontend-feature: ${error}` };
  }

  const names = {
    slug,
    Pascal: toPascalCase(slug),
    camel: toCamelCase(slug),
    human: slug.replace(/-/g, ' '),
  };

  const changes = new ChangeSet(`generate:frontend-feature — ${names.slug}`);
  changes.create(`${FRONTEND}/features/${slug}/index.ts`, barrelFile(names));
  changes.create(`${FRONTEND}/features/${slug}/components/${slug}-panel.tsx`, panelFile(names));
  changes.create(`${FRONTEND}/features/${slug}/components/${slug}-panel.test.tsx`, panelTestFile(names));
  changes.create(`${FRONTEND}/features/${slug}/hooks/use-${slug}.ts`, hookFile(names));
  changes.create(`${FRONTEND}/locales/en/${slug.replace(/-/g, '_')}.json`, localeFile(names, 'en'));
  changes.create(`${FRONTEND}/locales/ms/${slug.replace(/-/g, '_')}.json`, localeFile(names, 'ms'));
  changes.edit(QUERY_KEYS, (text) => addQueryKeyNamespace(text, names), `${names.camel} namespace`);

  return {
    changes,
    onDone: () => printNextSteps([
      `Translate \`locales/ms/${slug.replace(/-/g, '_')}.json\`. The generator copied the English strings with a TODO(ms) prefix rather than inventing Malay — \`locales.test.ts\` only checks that the KEYS match, so an untranslated value ships silently unless it is marked.`,
      `Register the locale namespace wherever the app assembles messages, or \`useTranslations('${slug.replace(/-/g, '_')}')\` resolves to nothing at runtime.`,
      `Add the API client in \`lib/api/${slug}-api.ts\` — typed from \`src/generated\`, never hand-written.`,
      `The page itself belongs in \`app/\`; a feature never owns a route. Import the panel from \`@/features/${slug}\`, and only through the barrel.`,
      'Run `npm run verify:fast` — ESLint now applies the feature-boundary rules to this directory automatically, because it reads the feature list from the folder listing.',
    ]),
  };
}

function barrelFile(names) {
  return `/**
 * Public surface of \`features/${names.slug}\` (PLAN §4.2.6-B4).
 *
 * Only what a route or another layer legitimately composes belongs here. Form internals, sub-components
 * and clients stay unexported — a caller that can import half a flow will, and then the hook that owns
 * the flow's rules is optional.
 *
 * \`NO_FEATURE_INTERNALS\` in \`eslint.config.mjs\` makes this structural: outside code may import
 * \`@/features/${names.slug}\` and nothing deeper.
 */
export { ${names.Pascal}Panel } from './components/${names.slug}-panel';
export { use${names.Pascal} } from './hooks/use-${names.slug}';
`;
}

function panelFile(names) {
  return `'use client';

import { useTranslations } from 'next-intl';
import { use${names.Pascal} } from '../hooks/use-${names.slug}';

/**
 * TODO: one paragraph on what this panel is, citing the design-system section it implements.
 *
 * Conventions this file must keep — each is a gate, not a preference:
 *
 * - **Every string is translated.** No literal user-facing text; \`locales.test.ts\` compares key sets
 *   across \`en\` and \`ms\`, so an English-only string is a build failure in the honest case and a
 *   silently untranslated UI in the careless one.
 * - **No \`dangerouslySetInnerHTML\`.** Model and user text is rendered as a React text child so React
 *   escapes it. Markdown rendering and its sanitisation belong to task 36.
 * - **Icon-only controls carry a translated \`aria-label\`, and controls are ≥44 px** (design system
 *   §3.5, §6.1).
 * - **State is never inferred from colour alone** (§10.1). If a state matters, it has words.
 */
export function ${names.Pascal}Panel() {
  const t = useTranslations('${names.slug.replace(/-/g, '_')}');
  const { data, isPending, isError } = use${names.Pascal}();

  if (isPending) {
    return <p>{t('loading')}</p>;
  }
  if (isError) {
    // §7.3: a failure stays on screen with a way out of it, never a silent empty state.
    return <p role="alert">{t('error')}</p>;
  }

  return (
    <section aria-labelledby="${names.slug}-heading">
      <h1 id="${names.slug}-heading">{t('title')}</h1>
      {/* TODO: render \`data\`. */}
      <p>{String(data ?? '')}</p>
    </section>
  );
}
`;
}

function panelTestFile(names) {
  return `import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { describe, expect, it } from 'vitest';
import en${names.Pascal} from '@/locales/en/${names.slug.replace(/-/g, '_')}.json';
import { ${names.Pascal}Panel } from './${names.slug}-panel';

/**
 * TODO: assert behaviour, not markup.
 *
 * The tests worth having here are the ones that name a failure: what a user sees while the request is
 * in flight, what they see when it fails, and that a retry does not duplicate anything. A test that
 * asserts a heading exists passes for every future version of a panel that no longer works.
 *
 * Query the DOM the way a user reaches it — \`getByRole\`, \`getByLabelText\` — so a test breaks when
 * the control stops being reachable, which is the accessibility regression nothing else catches.
 */
function renderPanel() {
  return render(
    <NextIntlClientProvider locale="en" messages={{ ${names.slug.replace(/-/g, '_')}: en${names.Pascal} }}>
      <${names.Pascal}Panel />
    </NextIntlClientProvider>,
  );
}

describe('${names.Pascal}Panel', () => {
  it('TODO: replace with a test that names the failure it prevents', () => {
    renderPanel();

    expect(screen.getByText(en${names.Pascal}.loading)).toBeInTheDocument();
  });
});
`;
}

function hookFile(names) {
  return `'use client';

import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '@/lib/query/query-keys';

/**
 * TODO: the feature's data access.
 *
 * <p>Two rules this file exists to keep:
 *
 * - **The key comes from \`queryKeys\`**, never an inline array. PLAN §4.2.6-G: a typo in one inline
 *   key produces a second cache entry that never invalidates, and nothing fails — a user sees stale
 *   data on one screen, eventually.
 * - **The fetch lives in \`lib/api/\`**, typed from the generated client. A hook that calls \`fetch\`
 *   directly bypasses the base URL, the credentials, the correlation id, and the error translation
 *   that \`apiRequest\` applies to everything else.
 */
export function use${names.Pascal}() {
  return useQuery({
    queryKey: queryKeys.${names.camel}.all,
    queryFn: () => {
      throw new Error('TODO: call the typed client in lib/api/${names.slug}-api.ts');
    },
  });
}
`;
}

/**
 * The locale file, identical keys in both languages.
 *
 * Malay values are the English text prefixed `TODO(ms):` — deliberately visible, because
 * `locales.test.ts` compares key *sets* and would happily pass a file whose values are all English.
 * A generator has no business inventing user-facing translations, and an unmarked copy is the version
 * that ships.
 */
function localeFile(names, locale) {
  const strings = {
    title: names.human.charAt(0).toUpperCase() + names.human.slice(1),
    loading: 'Loading…',
    error: 'Something went wrong. Please try again.',
  };
  const values = locale === 'ms'
    ? Object.fromEntries(Object.entries(strings).map(([key, value]) => [key, `TODO(ms): ${value}`]))
    : strings;
  return stringifyJson(values);
}

/**
 * Inserts the namespace into `queryKeys`, alphabetically.
 *
 * <b>Deliberately not `insertSorted`.</b> That helper walks a flat list, and `queryKeys` is nested —
 * its members are multi-line objects. Pointed at this file it compares the *inner* lines (`all:`,
 * `currentUser:`) against the new namespace name, and drops the block inside whichever member it
 * reached first: `queryKeys.auth.itinerary`. The file still parses, so `npm run typecheck` fails on
 * the call site rather than on the mangled registry, which is a confusing way to find out. It did
 * exactly that on the first run of this generator.
 *
 * So this walks only top-level namespace openings, and steps back over the JSDoc belonging to the
 * member it lands before — otherwise the new block wedges between a comment and the thing it
 * documents, which is legal and reads as though the comment were about the wrong namespace.
 */
function addQueryKeyNamespace(text, names) {
  if (new RegExp(`^ {2}${names.camel}: \\{`, 'm').test(text)) {
    return text;
  }
  const block = [`  ${names.camel}: {`, `    all: ['${names.slug}'] as const,`, '  },'];
  const lines = text.split('\n');
  const closing = lines.findIndex((line) => line.startsWith('} as const;'));
  if (closing === -1) {
    throw new Error('addQueryKeyNamespace: could not find the end of the queryKeys object');
  }

  let target = closing;
  for (let index = 0; index < closing; index += 1) {
    const match = /^ {2}([a-zA-Z][a-zA-Z0-9]*): \{$/.exec(lines[index]);
    if (match !== null && match[1] > names.camel) {
      target = index;
      while (target > 0 && /^\s*(\*|\/\*)/.test(lines[target - 1])) {
        target -= 1;
      }
      break;
    }
  }

  lines.splice(target, 0, ...block);
  return lines.join('\n');
}
