/**
 * The vertical-slice generators (review §6.D).
 *
 * ```
 * npm run generate:error-code      -- version_conflict CONFLICT "Optimistic-lock mismatch"
 * npm run generate:migration       -- create_itinerary_tables
 * npm run generate:backend-slice   -- research-job
 * npm run generate:frontend-feature -- itinerary
 * ```
 *
 * <b>What these are for, precisely.</b> Not typing speed — an agent types fast. They exist because
 * four of this repository's repetitive structures have registration steps whose omission is *silent*:
 * an error code missing from a locale file reaches a user as a raw identifier, a migration that picked
 * a taken version fails at deploy time in whichever environment merged second, a service method
 * missing `@TransactionalWrite` is a partial write nothing reports, and a query key written inline
 * creates a second cache entry that never invalidates. Every one of those is a mechanical error with a
 * non-mechanical symptom, which is the worst combination there is.
 *
 * <b>Skeletons only</b>, as the review requires: business logic stays with the agent. Every generated
 * file compiles and every one carries `TODO`s where a decision belongs. What is filled in is the part
 * where the codebase has already decided — the package line, the transaction boundary, the registry
 * entry, the locale pair.
 *
 * <b>Safety.</b> Nothing is overwritten, ever; a collision aborts the whole run before the first byte.
 * The plan prints before anything is written, and `--dry-run` stops there.
 */

import * as backendSlice from './lib/generators/backend-slice.mjs';
import * as errorCode from './lib/generators/error-code.mjs';
import * as frontendFeature from './lib/generators/frontend-feature.mjs';
import * as migration from './lib/generators/migration.mjs';

const GENERATORS = new Map([
  ['error-code', errorCode],
  ['migration', migration],
  ['backend-slice', backendSlice],
  ['frontend-feature', frontendFeature],
]);

const argv = process.argv.slice(2);
const kind = argv[0];
const args = argv.slice(1).filter((arg) => arg !== '--dry-run');
const dryRun = argv.includes('--dry-run');

if (kind === undefined || !GENERATORS.has(kind)) {
  console.error('Usage: node scripts/generate.mjs <kind> [args…] [--dry-run]\n');
  console.error('Available:');
  for (const generator of GENERATORS.values()) {
    console.error(`  npm run ${generator.usage}`);
  }
  console.error('\n  --dry-run   print the plan and write nothing');
  process.exit(2);
}

const { error, changes, onDone } = GENERATORS.get(kind).plan(args);

if (error !== undefined && error !== null) {
  console.error(`generate:${kind}: ${error}\n`);
  console.error(`Usage: npm run ${GENERATORS.get(kind).usage}`);
  process.exit(2);
}

changes.print();

const unsafe = changes.assertSafe();
if (unsafe !== null) {
  console.error(unsafe);
  process.exit(1);
}

if (dryRun) {
  console.log('--dry-run: nothing written.');
  process.exit(0);
}

changes.apply();
onDone?.();
