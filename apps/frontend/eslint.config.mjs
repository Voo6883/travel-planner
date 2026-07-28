import { FlatCompat } from '@eslint/eslintrc';

const compat = new FlatCompat({ baseDirectory: import.meta.dirname });

const config = [
  {
    ignores: [
      '.next/**',
      'node_modules/**',
      'next-env.d.ts',
      'src/generated/**',
      // Serwist's compiled service worker (PLAN §4.2.11: "build output; do not hand-edit").
      // It is bundled and minified, so linting it reports on Serwist's code, not ours —
      // `src/sw.ts` is the file that is actually linted.
      'public/sw.js',
      'public/sw.js.map',
    ],
  },
  ...compat.extends('next/core-web-vitals', 'next/typescript'),
  {
    rules: {
      // PLAN §4.2.6-A: no `any`. Use `unknown` + narrowing, or generated types.
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
];

export default config;
