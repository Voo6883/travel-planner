import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { semanticTokens, type SemanticTokenName } from './design-tokens';

/**
 * `globals.css` mirrors `design-tokens.ts` by hand, and this is what stops the mirror from
 * rotting. Task 03 left a note that task 11 would "replace this hand-mirroring with generated
 * output"; a drift test buys the same guarantee without introducing a generated file that nobody
 * is allowed to edit and that every contributor has to remember to regenerate.
 *
 * A drift here is not cosmetic. The token module feeds the Ant theme and the Tailwind config,
 * while the CSS variables feed every utility class — so a value that disagrees produces a button
 * whose fill and whose hover state come from two different palettes.
 */
// Resolved from the project root: Vitest rewrites `import.meta.url` during transform, so it is
// not a usable file URL here.
const css = readFileSync(resolve(process.cwd(), 'src/styles/globals.css'), 'utf8');

/** Three blocks, one decision (§13): light default, `system` via media query, explicit `.dark`. */
const blocks = {
  light: extractBlock(':root {'),
  system_dark: extractBlock(':root:not(.light) {'),
  explicit_dark: extractBlock(':root.dark {'),
} as const;

describe('design tokens', () => {
  it.each(Object.keys(semanticTokens.light) as SemanticTokenName[])('light: --%s matches design-tokens.ts', (name) => {
    expect(blocks.light[`--${name}`]).toBe(normalise(semanticTokens.light[name]));
  });

  it.each(Object.keys(semanticTokens.dark) as SemanticTokenName[])(
    'dark: --%s matches design-tokens.ts in both dark selectors',
    (name) => {
      const expected = normalise(semanticTokens.dark[name]);
      expect(blocks.system_dark[`--${name}`]).toBe(expected);
      expect(blocks.explicit_dark[`--${name}`]).toBe(expected);
    },
  );

  it('defines no CSS variable that the token module does not declare', () => {
    const declared = new Set(Object.keys(semanticTokens.light).map((name) => `--${name}`));
    for (const block of Object.values(blocks)) {
      for (const name of Object.keys(block)) {
        expect(declared, `${name} is only in globals.css`).toContain(name);
      }
    }
  });
});

/** Reads the `--name: value;` pairs of one block. `color-scheme` is not a token and is skipped. */
function extractBlock(selector: string): Record<string, string> {
  const start = css.indexOf(selector);
  if (start === -1) {
    throw new Error(`globals.css has no \`${selector}\` block`);
  }
  const body = css.slice(start + selector.length, css.indexOf('}', start));

  return Object.fromEntries(
    [...body.matchAll(/(--[a-z-]+):\s*([^;]+);/g)].map((match) => [match[1] ?? '', normalise(match[2] ?? '')]),
  );
}

/** Case and whitespace are not meaningful in a colour value; a different value is. */
function normalise(value: string): string {
  return value.trim().toLowerCase().replaceAll(' ', '');
}
