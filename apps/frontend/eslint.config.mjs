import { readdirSync } from 'node:fs';
import { join } from 'node:path';
import { FlatCompat } from '@eslint/eslintrc';

const compat = new FlatCompat({ baseDirectory: import.meta.dirname });

/**
 * Feature module names, read from disk rather than hard-coded (PLAN §4.2, tasks/15).
 *
 * Adding `features/booking/` in task 32 must not require also remembering to extend this list —
 * a boundary rule that silently stops covering new code is worse than no rule, because it reads
 * as enforced.
 */
const FEATURES = readdirSync(join(import.meta.dirname, 'src', 'features'), { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name);

// ---------------------------------------------------------------------------------------------
// Layer boundaries (tasks/15: "Import-boundary rules for app/features/shared/generated").
//
// IMPORTANT — why every zone below restates its full pattern list.
//
// `no-restricted-imports` does NOT merge across flat-config objects. When two config objects both
// match a file, the later one REPLACES the rule rather than adding to it. An earlier draft of this
// file expressed the four boundaries as four cascading blocks, the last of which matched `src/**`;
// the result was that three of the four rules silently did nothing, and `npm run lint` passed on a
// file importing another feature. The zones below are therefore deliberately DISJOINT, and each
// carries every restriction that applies to it.
//
// If you add a zone, make sure its `files`/`ignores` cannot overlap an existing one, then re-run
// the probe described in docs/QUALITY-GATES.md to prove the rule actually bites.
// ---------------------------------------------------------------------------------------------

const NO_APP = {
  group: ['@/app/*', '@/app/**'],
  message: 'src/app is the routing layer. Nothing may import from it.',
};

const NO_FEATURES = {
  group: ['@/features/*', '@/features/*/**'],
  message:
    'Shared code must not import a feature. Invert it: accept what you need as a prop, or move ' +
    'the component into the feature that owns it.',
};

const NO_GENERATED = {
  group: ['@/generated', '@/generated/**'],
  message:
    'Import from src/lib/api instead. The generated client is regenerated wholesale, so only the ' +
    'API layer should depend on its shape.',
};

const NO_FEATURE_INTERNALS = {
  group: ['@/features/*/**'],
  message:
    'Import the feature barrel (@/features/<name>), not a file inside it. Anything needed from ' +
    "outside belongs in that feature's index.ts.",
};

/** Feature X may reach its own internals, never a sibling's (PLAN §4.0.8 vertical slices). */
const noSiblingFeatures = (feature) => ({
  group: ['@/features/*', '@/features/*/**', `!@/features/${feature}`, `!@/features/${feature}/**`],
  message:
    `features/${feature} must not import another feature. Lift the shared part into ` +
    'src/components, src/hooks or src/lib, or let the app layer wire the two together.',
});

const restrict = (...patterns) => ({
  'no-restricted-imports': ['error', { patterns }],
});

const config = [
  {
    ignores: [
      '.next/**',
      'node_modules/**',
      'next-env.d.ts',
      'src/generated/**',
      'coverage/**',
      // Serwist's compiled service worker (PLAN §4.2.11: "build output; do not hand-edit").
      // It is bundled and minified, so linting it reports on Serwist's code, not ours —
      // `src/sw.ts` is the file that is actually linted.
      'public/sw.js',
      'public/sw.js.map',
    ],
  },
  ...compat.extends('next/core-web-vitals', 'next/typescript'),

  // -------------------------------------------------------------------------------------------
  // Strict TypeScript (PLAN §4.2.6-A, AI-AGENT-WORKFLOW.md).
  // -------------------------------------------------------------------------------------------
  {
    rules: {
      // PLAN §4.2.6-A: no `any`. Use `unknown` + narrowing, or generated types.
      '@typescript-eslint/no-explicit-any': 'error',
      // A non-null assertion overrides the compiler on exactly the question it was asked.
      '@typescript-eslint/no-non-null-assertion': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        // A leading underscore is the conventional "deliberately unused" marker — required for
        // positional callback params and destructured rest-omission.
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],
      // PLAN §1067 / AI-AGENT-WORKFLOW.md: 120 columns, matching Checkstyle's LineLength on the
      // backend so one repository does not have two answers.
      'max-len': [
        'error',
        {
          code: 120,
          ignoreUrls: true,
          ignoreStrings: true,
          ignoreTemplateLiterals: true,
          ignoreRegExpLiterals: true,
          ignorePattern: '^import\\s.+from\\s.+;$',
        },
      ],
    },
  },

  // Zone: shared UI, hooks and styles. Reusable by anything, so they may know about nothing above
  // them — not a feature, not a route, not the generated client.
  //
  // `src/components/layout/**` used to be carved out of this zone: app-shell.tsx and
  // account-menu.tsx called useCurrentUser / useSignOut from @/features/auth, which is a shared
  // component importing a feature. F-25 recorded it, the 2026-07-29 review named it again, and it is
  // **closed** — the two components take identity as props, and
  // features/auth/components/authenticated-app-shell.tsx supplies it. The exception is gone rather
  // than narrowed, so nothing in shared code has a precedent to point at.
  {
    files: ['src/components/**', 'src/hooks/**', 'src/styles/**'],
    ignores: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    rules: restrict(NO_FEATURES, NO_APP, NO_GENERATED),
  },

  // Zone: the typed API layer. The one place allowed to see the generated client.
  {
    files: ['src/lib/api/**'],
    ignores: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    rules: restrict(NO_FEATURES, NO_APP),
  },

  // Zone: the rest of src/lib.
  {
    files: ['src/lib/**'],
    ignores: ['src/lib/api/**', 'src/**/*.test.ts', 'src/**/*.test.tsx'],
    rules: restrict(NO_FEATURES, NO_APP, NO_GENERATED),
  },

  // Zone: one per feature. Generated from the directory listing so a new feature is covered the
  // moment it exists.
  ...FEATURES.map((feature) => ({
    files: [`src/features/${feature}/**`],
    ignores: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    rules: restrict(noSiblingFeatures(feature), NO_APP, NO_GENERATED),
  })),

  // Zone: the routing layer. May compose features, but only through their public barrel.
  {
    files: ['src/app/**'],
    ignores: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    rules: restrict(NO_FEATURE_INTERNALS, NO_GENERATED),
  },

  // Zone: tests and fixtures. Listed LAST so it wins for any *.test.* file in any directory above.
  // Contract-accurate fixtures need the generated types, and a test may legitimately compose two
  // features to prove they integrate.
  {
    files: ['src/test/**', 'src/**/*.test.ts', 'src/**/*.test.tsx'],
    rules: restrict(NO_APP),
  },
];

export default config;
