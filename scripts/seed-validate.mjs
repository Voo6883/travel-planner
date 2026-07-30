/**
 * `npm run seed:validate` — validate the sample knowledge seed against the domain's own invariants.
 *
 * **What it runs.** `SampleSeedValidationTest`, which drives the real `SampleKnowledgeReader` over the
 * real committed files. No database, no Docker, no Spring context — seconds, not minutes.
 *
 * **Why a script for one test class.** This is a convenience over the same gate, deliberately not a
 * second source of truth. The check itself lives inside `SampleKnowledgeReader.readDestination()`, the
 * only path that reads a destination file, so it also runs during a real seed and in `./gradlew test` —
 * which is what CI and `npm run verify:fast` already execute. Nothing here can pass while that fails.
 *
 * The reason to have it anyway is the seed-curation loop that tasks 40 and 41 are made of: a curator
 * editing `tokyo-jp.json` wants the answer about the seed in a few seconds, not the whole backend unit
 * suite, and wants a failure that reads as "your file is wrong" rather than "a test failed". `--help`
 * spells out what the validator checks, so the answer to "what am I allowed to write?" is one command
 * away from the file being edited.
 *
 * See F-44 in `tasks/STATUS.md` for the defect this closes.
 */

import { spawnSync } from 'node:child_process';
import { repoPath } from './lib/repo.mjs';

const isWindows = process.platform === 'win32';

const TEST_CLASS = 'com.travelplanner.infrastructure.knowledge.SampleSeedValidationTest';

const SEED_DIR = 'apps/backend/src/main/resources/knowledge/sample/';

const passThrough = process.argv.slice(2);

if (passThrough.includes('--help') || passThrough.includes('-h')) {
  help();
  process.exit(0);
}

// An absolute path for the same reason `verify.mjs` uses one: with `shell: true` on Windows the
// wrapper's own directory is not searched, so a bare `gradlew.bat` is not found even from beside it.
const gradle = repoPath('apps', 'backend', isWindows ? 'gradlew.bat' : 'gradlew');

console.log(`seed:validate: ${SEED_DIR}*.json against the domain records they become`);

const result = spawnStep(gradle, ['--no-daemon', 'test', '--tests', TEST_CLASS, ...passThrough], {
  cwd: repoPath('apps', 'backend'),
  stdio: 'inherit',
});

const status = result.status ?? 1;

if (status === 0) {
  console.log('\nseed:validate: every seed file satisfies the domain.\n');
} else {
  // The Gradle failure above already lists each problem the validator collected, one per line, naming
  // the file and the offending value. Repeating them here would only bury them.
  console.error([
    '',
    'seed:validate: the seed does not satisfy the domain.',
    '',
    `Each line above names a file in ${SEED_DIR} and what is wrong with it. These are the`,
    'failures that would otherwise appear when a row is READ — after the seed had loaded green.',
    '',
    'Run `npm run seed:validate -- --help` for what is checked and why.',
    '',
  ].join('\n'));
}

process.exit(status);

/**
 * What the validator checks.
 *
 * <p>Written out here rather than left in Java javadoc because the person who needs it is editing JSON,
 * and the honest ordering is "the surprising rules first". Every entry is enforced by constructing the
 * domain object the node becomes, so this text describes behaviour rather than duplicating it.
 */
function help() {
  console.log([
    'npm run seed:validate [-- <gradle args>]',
    '',
    `Validates every file in ${SEED_DIR} by building the domain objects it will become,`,
    'so an invariant that would fail on READ fails at seed time instead.',
    '',
    'The rules that catch people:',
    '',
    '  amount + currency   Scale is the currency\'s, not the column\'s. `price_history.amount` is',
    '                      numeric(12,2) for every currency, but JPY has no minor units — 4000.10 JPY',
    '                      is refused, 4000 JPY is fine. This was F-44.',
    '  observed_on         The first of the month. price_history is a monthly series; a mid-month date',
    '                      makes two "consecutive" figures mean something else.',
    '  timezone            A real IANA zone, resolved against the JVM\'s tzdb. `Asia/Tokio` is not one.',
    '  latitude/longitude  Set together or both absent, and within range. A swap puts a neighbourhood',
    '                      in the wrong ocean — or, if both happen to be in range, nowhere detectable.',
    '  replaced_app_key    A lower-case slug. Not a slug means the suppression matches nothing and the',
    '                      warning is silently absent from the app pack.',
    '  ios_url/android_url At least one. An app nobody can install is not an app-pack entry.',
    '  area_slug           Must name an area the same file defines. Unknown means a POI attached to',
    '                      nothing, which cannot be grouped geographically.',
    '  seasonality         Twelve distinct months, or the destination cannot reach FULL coverage.',
    '  slugs               Unique per destination, for areas and for POIs.',
    '',
    'Every problem in the file is reported in one pass, so a bad seed is one round of fixes.',
    '',
    'This runs the same check a real seed run performs; it cannot pass while `./gradlew test` fails.',
  ].join('\n'));
}

/** The two Windows traps `verify.mjs` and `integration-db.mjs` both document. */
function spawnStep(command, args, options) {
  const needsShell = isWindows && /\.(cmd|bat)$/i.test(command);
  const quote = (value) => (/\s/.test(value) ? `"${value}"` : value);
  return spawnSync(needsShell ? quote(command) : command, needsShell ? args.map(quote) : args, {
    ...options,
    shell: needsShell,
  });
}
