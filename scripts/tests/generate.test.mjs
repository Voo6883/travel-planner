/**
 * Tests for the vertical-slice generators (review §6.D).
 *
 * `node:test` and `node:assert`, which ship with Node 22 — the root `package.json` has no
 * dependencies by design (PLAN §4.0.0: orchestration only), and a test framework here would be the
 * first one.
 *
 * <b>Every test drives the real `plan()` and applies its real transforms to synthetic text.</b> None
 * of them writes to the repository, so the suite cannot leave a half-generated slice behind — which
 * matters more than usual, because the thing under test is a file writer.
 *
 * The registry-insertion tests are regressions, not hypotheticals. Both bugs happened on the first
 * run of these generators: the query-key namespace landed *inside* `queryKeys.auth` because a
 * flat-list helper compared nested lines, and the error-code Java constant has to negotiate an enum
 * whose last entry carries the `;`. Neither produced a syntax error. The first surfaced as a type
 * error in an unrelated call site; the second would have surfaced as two semicolons if it had gone
 * wrong in the other direction, and as silent misordering if it had not.
 */

import assert from 'node:assert/strict';
import { test } from 'node:test';
import * as backendSlice from '../lib/generators/backend-slice.mjs';
import * as errorCode from '../lib/generators/error-code.mjs';
import * as frontendFeature from '../lib/generators/frontend-feature.mjs';
import * as migration from '../lib/generators/migration.mjs';
import {
  insertSorted,
  toCamelCase,
  toJavaPackageSegment,
  toPascalCase,
  toScreamingSnakeCase,
  toSnakeCase,
  validateSlug,
} from '../lib/generate-support.mjs';

/** Applies the transform registered for `relativePath` to `text`, the way `ChangeSet.apply` would. */
function applyEdit(changes, relativePath, text, occurrence = 0) {
  const edits = changes.edits.filter((edit) => edit.relativePath === relativePath);
  assert.ok(edits.length > occurrence, `no edit #${occurrence} registered for ${relativePath}`);
  return edits[occurrence].transform(text);
}

// ---------------------------------------------------------------------------------------------
// Naming
// ---------------------------------------------------------------------------------------------

test('name conversions round-trip the shapes each layer needs', () => {
  assert.equal(toPascalCase('research-job'), 'ResearchJob');
  assert.equal(toCamelCase('research-job'), 'researchJob');
  // Java packages are lower-case with no separators — `researchjob`, not `research_job`.
  assert.equal(toJavaPackageSegment('research-job'), 'researchjob');
  assert.equal(toScreamingSnakeCase('research-job'), 'RESEARCH_JOB');
  assert.equal(toSnakeCase('researchJob'), 'research_job');
  assert.equal(toSnakeCase('research-job'), 'research_job');
});

test('a slug is validated, never normalised', () => {
  assert.equal(validateSlug('research-job').slug, 'research-job');

  // Rejected rather than rewritten: `Research Job` almost certainly means `research-job`, but
  // silently fixing it means the agent's next command uses the name it typed and addresses nothing.
  for (const bad of ['Research Job', 'research_job', '-research', 'research-', '9lives', '', '  ']) {
    const { slug, error } = validateSlug(bad);
    assert.equal(slug, null, `${JSON.stringify(bad)} should be rejected`);
    assert.match(error, /lower-kebab-case|required/);
  }
});

// ---------------------------------------------------------------------------------------------
// insertSorted — the flat-list helper
// ---------------------------------------------------------------------------------------------

test('insertSorted places a line in order and is idempotent', () => {
  const before = ['list:', '  - alpha', '  - gamma', 'end:'].join('\n');
  const options = {
    after: 'list:',
    before: (line) => line === 'end:',
    line: '  - beta',
    sortKey: (line) => line.replace('-', '').trim(),
  };

  const after = insertSorted(before, options);
  assert.equal(after, ['list:', '  - alpha', '  - beta', '  - gamma', 'end:'].join('\n'));
  // Re-running a generator must not duplicate a registry entry.
  assert.equal(insertSorted(after, options), after);
});

test('insertSorted appends when the new line sorts last', () => {
  const before = ['list:', '  - alpha', 'end:'].join('\n');
  const after = insertSorted(before, {
    after: 'list:',
    before: (line) => line === 'end:',
    line: '  - zulu',
    sortKey: (line) => line.replace('-', '').trim(),
  });

  assert.equal(after, ['list:', '  - alpha', '  - zulu', 'end:'].join('\n'));
});

// ---------------------------------------------------------------------------------------------
// generate:error-code
// ---------------------------------------------------------------------------------------------

test('error-code registers all six steps of the documented procedure', () => {
  const { changes, error } = errorCode.plan(['itinerary_locked', 'CONFLICT', 'Being regenerated']);

  assert.equal(error, undefined);
  const touched = changes.edits.map((edit) => edit.relativePath);
  assert.deepEqual(new Set(touched), new Set([
    'apps/backend/src/main/java/com/travelplanner/api/openapi/errors.yaml',
    'apps/backend/src/main/java/com/travelplanner/api/error/ApiErrorCode.java',
    'apps/frontend/src/locales/en/common.json',
    'apps/frontend/src/locales/ms/common.json',
    // Step 5, which errors.yaml's header omitted until this generator tripped over it: without it
    // `npm run typecheck` fails in a file the author never opened.
    'apps/frontend/src/lib/api/api-error.ts',
  ]));
  assert.equal(changes.creations.length, 0, 'registering a code creates no files');
});

test('error-code rejects a status that is not in the catalog vocabulary', () => {
  // The constant goes straight into Java, so `HttpStatus.CONFLIC` would be a compile error found two
  // commands later rather than an argument error found now.
  assert.match(errorCode.plan(['x_code', 'CONFLIC', 'Meaning']).error, /not a status/);
  assert.match(errorCode.plan(['x_code', 'IM_A_TEAPOT', 'Meaning']).error, /not a status/);
});

test('error-code rejects a code that is not snake_case, and one already registered', () => {
  assert.match(errorCode.plan(['ItineraryLocked', 'CONFLICT', 'Meaning']).error, /snake_case/);
  assert.match(errorCode.plan(['version_conflict', 'CONFLICT', 'Meaning']).error, /already/);
});

test('error-code requires a meaning, because it becomes the catalog row', () => {
  assert.match(errorCode.plan(['x_code', 'CONFLICT']).error, /meaning is required/);
  assert.match(errorCode.plan(['x_code', 'CONFLICT', '   ']).error, /meaning is required/);
});

test('error-code inserts the Java constant alphabetically, above its neighbour’s javadoc', () => {
  const enumSource = [
    'public enum ApiErrorCode {',
    '',
    '    /** Alpha. */',
    '    ALPHA("alpha", HttpStatus.BAD_REQUEST),',
    '',
    '    /**',
    '     * Zulu, documented across several lines.',
    '     */',
    '    ZULU("zulu", HttpStatus.CONFLICT);',
    '',
    '    private final String code;',
    '}',
  ].join('\n');

  const { changes } = errorCode.plan(['middle', 'NOT_FOUND', 'A middling failure']);
  const after = applyEdit(changes, 'apps/backend/src/main/java/com/travelplanner/api/error/ApiErrorCode.java', enumSource);
  const lines = after.split('\n');

  const inserted = lines.findIndex((line) => line.includes('MIDDLE("middle"'));
  const zuluDoc = lines.findIndex((line) => line.includes('Zulu, documented'));
  assert.ok(inserted > 0, 'the constant was inserted');
  assert.ok(inserted < zuluDoc, 'it sorts before ZULU');
  // The block must land ABOVE the `/**` that documents ZULU, not between the comment and ZULU —
  // that is legal Java and reads as though the comment described the wrong constant.
  assert.match(lines[inserted - 1], /A middling failure\./);
  assert.match(lines[inserted - 2], /^\s*$/);
  assert.equal((after.match(/;/g) ?? []).length, enumSource.match(/;/g).length,
      'exactly one enum terminator survives');
});

test('error-code moves the terminator when the new constant sorts last', () => {
  const enumSource = [
    'public enum ApiErrorCode {',
    '',
    '    /** Alpha. */',
    '    ALPHA("alpha", HttpStatus.BAD_REQUEST);',
    '',
    '    private final String code;',
    '}',
  ].join('\n');

  const { changes } = errorCode.plan(['zzz_last', 'GONE', 'Sorts last']);
  const after = applyEdit(changes, 'apps/backend/src/main/java/com/travelplanner/api/error/ApiErrorCode.java', enumSource);

  // The old final constant gives up its `;` and the new one takes it. Getting this backwards is a
  // compile error, which is the good case; getting the ORDER wrong is silent.
  assert.match(after, /ALPHA\("alpha", HttpStatus\.BAD_REQUEST\),/);
  assert.match(after, /ZZZ_LAST\("zzz_last", HttpStatus\.GONE\);/);
  assert.equal((after.match(/;/g) ?? []).length, enumSource.match(/;/g).length);
});

// ---------------------------------------------------------------------------------------------
// generate:frontend-feature
// ---------------------------------------------------------------------------------------------

test('frontend-feature inserts a query-key namespace BETWEEN namespaces, never inside one', () => {
  // The regression. A flat-list insert compares `all:` and `currentUser:` — the nested lines — against
  // the new namespace name and drops the block inside whichever member it reaches first, producing
  // `queryKeys.auth.itinerary`. The file still parses, so the failure surfaces as a type error at the
  // call site rather than in the mangled registry.
  const registry = [
    'export const queryKeys = {',
    '  auth: {',
    "    all: ['auth'] as const,",
    "    currentUser: () => ['auth', 'me'] as const,",
    '  },',
    '  /**',
    '   * Platform health, documented.',
    '   */',
    '  platform: {',
    "    all: ['platform'] as const,",
    '  },',
    '} as const;',
  ].join('\n');

  const { changes } = frontendFeature.plan(['itinerary']);
  const after = applyEdit(changes, 'apps/frontend/src/lib/query/query-keys.ts', registry);
  const lines = after.split('\n');

  const authAt = lines.findIndex((line) => line === '  auth: {');
  const authCloseAt = lines.indexOf('  },', authAt);
  const insertedAt = lines.findIndex((line) => line === '  itinerary: {');
  const platformDocAt = lines.findIndex((line) => line.includes('Platform health'));

  assert.ok(insertedAt > authCloseAt, 'the namespace is outside auth, not nested in it');
  assert.ok(insertedAt < platformDocAt, "and above platform's own javadoc, not between it and platform");
  assert.equal((after.match(/itinerary: \{/g) ?? []).length, 1);
});

test('frontend-feature is idempotent on the query-key registry', () => {
  const registry = [
    'export const queryKeys = {',
    '  itinerary: {',
    "    all: ['itinerary'] as const,",
    '  },',
    '} as const;',
  ].join('\n');

  const { changes } = frontendFeature.plan(['itinerary']);
  assert.equal(applyEdit(changes, 'apps/frontend/src/lib/query/query-keys.ts', registry), registry);
});

test('frontend-feature writes both locales with identical keys', () => {
  const { changes } = frontendFeature.plan(['itinerary']);
  const locale = (language) => JSON.parse(changes.creations
      .find((file) => file.relativePath === `apps/frontend/src/locales/${language}/itinerary.json`).contents);

  const en = locale('en');
  const ms = locale('ms');
  // `locales.test.ts` compares key SETS, so identical keys are what keeps the build green…
  assert.deepEqual(Object.keys(en), Object.keys(ms));
  // …and every Malay value is marked, because that test would pass just as happily on English text.
  // An unmarked machine translation is the version that ships.
  for (const value of Object.values(ms)) {
    assert.match(value, /^TODO\(ms\): /);
  }
  for (const value of Object.values(en)) {
    assert.doesNotMatch(value, /^TODO\(ms\): /);
  }
});

test('frontend-feature exports only through the barrel', () => {
  const { changes } = frontendFeature.plan(['itinerary']);
  const barrel = changes.creations
      .find((file) => file.relativePath.endsWith('features/itinerary/index.ts')).contents;

  assert.match(barrel, /export \{ ItineraryPanel \}/);
  assert.match(barrel, /export \{ useItinerary \}/);
});

// ---------------------------------------------------------------------------------------------
// generate:migration
// ---------------------------------------------------------------------------------------------

test('migration derives the next version from the filenames on disk', () => {
  const { changes, error } = migration.plan(['create_itinerary_tables']);

  assert.equal(error, undefined);
  assert.equal(changes.creations.length, 1);
  const { relativePath, contents } = changes.creations[0];
  const version = Number(/V(\d+)__/.exec(relativePath)[1]);

  // Whatever the repository's head is, the generated file is exactly one past it — the property that
  // makes two branches unable to pick the same number from a stale note.
  assert.ok(version > 21, `expected a version past V21, got V${version}`);
  assert.match(relativePath, /^apps\/backend\/src\/main\/resources\/db\/migration\/V\d+__create_itinerary_tables\.sql$/);
  assert.match(contents, /timestamptz/);
  assert.match(contents, /numeric/);
});

test('migration rejects a name that MigrationContractTest would reject later', () => {
  // V{n}__{snake_case}.sql is asserted by the build. Failing here names the argument; failing there
  // names a regex.
  for (const bad of ['create-itinerary', 'CreateItinerary', '1_tables', '']) {
    assert.match(migration.plan([bad]).error, /snake_case/);
  }
});

// ---------------------------------------------------------------------------------------------
// generate:backend-slice
// ---------------------------------------------------------------------------------------------

test('backend-slice lays out every layer, and puts the transaction boundary in the template', () => {
  const { changes, error } = backendSlice.plan(['research-job']);

  assert.equal(error, undefined);
  const paths = changes.creations.map((file) => file.relativePath);
  assert.deepEqual(paths, [
    'apps/backend/src/main/java/com/travelplanner/domain/port/ResearchJobRepositoryPort.java',
    'apps/backend/src/main/java/com/travelplanner/application/researchjob/ResearchJobService.java',
    'apps/backend/src/main/java/com/travelplanner/application/researchjob/CreateResearchJobCommand.java',
    'apps/backend/src/main/java/com/travelplanner/application/researchjob/ResearchJobView.java',
    'apps/backend/src/main/java/com/travelplanner/api/controller/ResearchJobController.java',
    'apps/backend/src/main/java/com/travelplanner/infrastructure/persistence/ResearchJobRepositoryAdapter.java',
    'apps/backend/src/test/java/com/travelplanner/application/researchjob/ResearchJobServiceTest.java',
  ]);

  const service = changes.creations.find((file) => file.relativePath.endsWith('ResearchJobService.java')).contents;
  // The one omission nothing in the build catches: a write method with no transaction boundary is a
  // silent partial write. The template has it on, which is the entire reason it is a template.
  assert.match(service, /@TransactionalWrite\s+public UUID create\(/);
  assert.match(service, /@Transactional\(readOnly = true\)/);

  const controller = changes.creations.find((file) => file.relativePath.endsWith('ResearchJobController.java')).contents;
  // ArchUnit forbids it, but only after a build; the template simply never suggests it.
  assert.doesNotMatch(controller, /@Transactional/);
});

test('every generated Java file declares the package its path implies', () => {
  const { changes } = backendSlice.plan(['research-job']);

  for (const { relativePath, contents } of changes.creations) {
    const expected = relativePath
        .replace(/^apps\/backend\/src\/(main|test)\/java\//, '')
        .replace(/\/[^/]+\.java$/, '')
        .replace(/\//g, '.');
    assert.match(contents, new RegExp(`^package ${expected.replace(/\./g, '\\.')};`, 'm'),
        `${relativePath} must declare package ${expected}`);
  }
});

// ---------------------------------------------------------------------------------------------
// Safety
// ---------------------------------------------------------------------------------------------

test('a generator refuses to overwrite an existing file, and names every collision', () => {
  // `package.json` and `README.md` both exist, so the whole set is refused. Reporting only the first
  // would make an agent re-run to discover the second and assume the first fix was the only one.
  const { changes } = backendSlice.plan(['research-job']);
  changes.creations.push({ relativePath: 'package.json', contents: 'x' });
  changes.creations.push({ relativePath: 'README.md', contents: 'y' });

  const refusal = changes.assertSafe();
  assert.ok(refusal !== null, 'the change set must be refused');
  assert.match(refusal, /package\.json/);
  assert.match(refusal, /README\.md/);
  assert.match(refusal, /data-loss/);
});

test('a change set whose edit targets are missing is refused too', () => {
  const { changes } = frontendFeature.plan(['itinerary']);
  changes.edits.push({ relativePath: 'no/such/file.ts', transform: (text) => text });

  assert.match(changes.assertSafe(), /expected and are missing/);
});
