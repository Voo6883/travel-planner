/**
 * Writes `docs/generated/CODE-MAP.json` (review §6.E).
 *
 * `npm run code-map` regenerates it; `npm run code-map -- --check` fails when the committed copy is
 * stale, which is what CI runs. The file is committed rather than generated on demand for one
 * reason: an agent has to be able to *read* it before it is allowed to run anything, and a cloud
 * agent with no toolchain warmed up still gets the answers.
 *
 * The `--check` mode is the same pattern as the OpenAPI drift gate. Without it the committed map
 * silently ages, agents start acting on a stale port list, and the failure looks like the model
 * hallucinating a class name.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { buildCodeMap } from './lib/code-map.mjs';
import { formatBytes, readTextIfPresent, repoPath, toPosix } from './lib/repo.mjs';

const OUTPUT = repoPath('docs', 'generated', 'CODE-MAP.json');
const checkOnly = process.argv.slice(2).includes('--check');

const serialised = `${JSON.stringify(buildCodeMap(), null, 2)}\n`;
const committed = readTextIfPresent(OUTPUT);

if (checkOnly) {
  if (committed === serialised) {
    console.log(`code-map: ${toPosix(OUTPUT)} is up to date.`);
    process.exit(0);
  }
  console.error(`code-map: ${toPosix(OUTPUT)} is stale.\n`);
  console.error('Run `npm run code-map` and commit the result.\n');
  console.error(firstDifference(committed, serialised));
  process.exit(1);
}

mkdirSync(dirname(OUTPUT), { recursive: true });
writeFileSync(OUTPUT, serialised, 'utf8');
console.log(`code-map: wrote ${toPosix(OUTPUT)} (${formatBytes(Buffer.byteLength(serialised))}).`);

/**
 * One line of context on a stale map, rather than a full diff.
 *
 * A 40 KB JSON diff in a CI log is scrolled past. The first differing line is almost always enough
 * to say *what* changed — a new port, a new migration — and the fix is the same either way.
 */
function firstDifference(before, after) {
  if (before === null) {
    return 'The file does not exist yet.';
  }
  const beforeLines = before.split('\n');
  const afterLines = after.split('\n');
  for (let index = 0; index < Math.max(beforeLines.length, afterLines.length); index += 1) {
    if (beforeLines[index] !== afterLines[index]) {
      return [
        `First difference at line ${index + 1}:`,
        `  committed: ${beforeLines[index] ?? '(end of file)'}`,
        `  generated: ${afterLines[index] ?? '(end of file)'}`,
      ].join('\n');
    }
  }
  return 'The files differ only in trailing whitespace.';
}
