import { FlatCompat } from '@eslint/eslintrc';

const compat = new FlatCompat({ baseDirectory: import.meta.dirname });

const config = [
  {
    ignores: ['.next/**', 'node_modules/**', 'next-env.d.ts', 'src/generated/**'],
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
