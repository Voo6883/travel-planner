/**
 * `npm run generate:error-code -- <code> <HTTP_STATUS> "<meaning>"`
 *
 * Registering an error code is a five-file change that `errors.yaml`'s own header documents as a
 * numbered procedure. Every one of the five is mechanical, and each has a distinct failure mode when
 * it is the one that gets forgotten:
 *
 * | Missed step | What happens |
 * |---|---|
 * | `ErrorCode.enum` in `errors.yaml` | `OpenApiSpecTest` fails — caught, but a build cycle late |
 * | The catalog table row | Nothing fails. The registry stops describing itself |
 * | `ApiErrorCode.java` | `GlobalExceptionHandler` downgrades the code to `internal_error` at runtime |
 * | `locales/en/common.json` | The user reads a raw identifier: "version_conflict" |
 * | `locales/ms/common.json` | Same, for Malay only — so it passes every test somebody runs locally |
 * | `REGISTERED_ERROR_CODES` in `lib/api/api-error.ts` | `npm run typecheck` fails |
 * | `npm run codegen` | Contract drift; CI catches it |
 *
 * Two of those seven are silent. That is the whole case for a generator: not typing speed, but the
 * two failure modes no gate reports.
 *
 * <b>The seventh step was undocumented until this generator was written.</b> `errors.yaml`'s header
 * describes the procedure as five steps and does not mention `REGISTERED_ERROR_CODES` — the runtime
 * value list TypeScript needs to narrow an arbitrary server string. It is caught by a compile-time
 * exhaustiveness check, so it is not silent, but a procedure that omits a step it enforces is a
 * procedure somebody follows to completion and still fails. The header now lists all seven.
 *
 * <b>What it will not do.</b> It writes the English string and copies it into the Malay file marked
 * `TODO(ms)`, rather than inventing a translation. A machine-translated user-facing string that
 * nobody flagged is worse than a visibly untranslated one — the first ships, the second gets fixed.
 */

import { ChangeSet, insertSorted, printNextSteps, readJson, stringifyJson, toScreamingSnakeCase, withSortedKeys } from '../generate-support.mjs';
import { readText, repoPath } from '../repo.mjs';

const ERRORS_YAML = 'apps/backend/src/main/java/com/travelplanner/api/openapi/errors.yaml';
const ENUM_JAVA = 'apps/backend/src/main/java/com/travelplanner/api/error/ApiErrorCode.java';
const LOCALES = ['apps/frontend/src/locales/en/common.json', 'apps/frontend/src/locales/ms/common.json'];
const RUNTIME_LIST = 'apps/frontend/src/lib/api/api-error.ts';

/**
 * The Spring statuses this generator accepts, and the HTTP number each carries.
 *
 * An allow-list rather than a free string: the constant goes straight into Java, and
 * `HttpStatus.CONFLIC` is a compile error found two commands later. Restricting it to the statuses
 * PLAN §6.1 actually uses also stops a 418 from entering the catalog as a joke nobody removes.
 */
const STATUSES = new Map([
  ['BAD_REQUEST', 400],
  ['UNAUTHORIZED', 401],
  ['PAYMENT_REQUIRED', 402],
  ['FORBIDDEN', 403],
  ['NOT_FOUND', 404],
  ['CONFLICT', 409],
  ['GONE', 410],
  ['PAYLOAD_TOO_LARGE', 413],
  ['UNPROCESSABLE_ENTITY', 422],
  ['LOCKED', 423],
  ['TOO_MANY_REQUESTS', 429],
  ['INTERNAL_SERVER_ERROR', 500],
  ['NOT_IMPLEMENTED', 501],
  ['BAD_GATEWAY', 502],
  ['SERVICE_UNAVAILABLE', 503],
  ['GATEWAY_TIMEOUT', 504],
]);

export const usage = 'generate:error-code -- <snake_case_code> <HTTP_STATUS> "<one-line meaning>"';

export function plan(args) {
  const [code, status, meaning] = args;

  if (code === undefined || !/^[a-z][a-z0-9]*(_[a-z0-9]+)*$/.test(code)) {
    return { error: `'${code}' must be snake_case — it is the wire value and the i18n key suffix` };
  }
  if (status === undefined || !STATUSES.has(status)) {
    return {
      error: `'${status}' is not a status this catalog uses. One of:\n  ${[...STATUSES.keys()].join(', ')}`,
    };
  }
  if (meaning === undefined || meaning.trim() === '') {
    return { error: 'a one-line meaning is required — it becomes the catalog table row' };
  }

  const constant = toScreamingSnakeCase(code);
  const httpNumber = STATUSES.get(status);
  const alreadyRegistered = readText(repoPath(...ENUM_JAVA.split('/'))).includes(`"${code}"`);
  if (alreadyRegistered) {
    return { error: `'${code}' is already in ApiErrorCode. Nothing to do.` };
  }

  const changes = new ChangeSet(`generate:error-code — ${code} (${status}, ${httpNumber})`);

  changes.edit(ERRORS_YAML, (text) => addCatalogRow(text, code, httpNumber, meaning.trim()),
      'catalog table row');
  changes.edit(ERRORS_YAML, (text) => addEnumValue(text, code), 'ErrorCode.enum');
  changes.edit(ENUM_JAVA, (text) => addJavaConstant(text, constant, code, status, meaning.trim()),
      `${constant}("${code}", HttpStatus.${status})`);
  for (const locale of LOCALES) {
    changes.edit(locale, () => addLocaleString(locale, code, meaning.trim()),
        locale.includes('/ms/') ? 'Malay string, marked TODO(ms)' : 'English string');
  }
  changes.edit(RUNTIME_LIST, (text) => addRuntimeCode(text, code), 'REGISTERED_ERROR_CODES');

  return {
    changes,
    onDone: () => printNextSteps([
      `Replace the placeholder text in ${LOCALES[0]} — the generator used the catalog meaning, which is written for a developer, not for a traveller.`,
      `Translate the TODO(ms) entry in ${LOCALES[1]}. A generator will not invent a user-facing translation.`,
      'Run `npm run codegen` and commit the regenerated client (step 5 of the procedure in errors.yaml).',
      `Throw it from a DomainException subclass — a registered code nothing raises is dead weight.`,
      'Run `npm run verify:fast`.',
    ]),
  };
}

/** The `| code | HTTP | meaning |` row inside `ErrorCode`'s description table. */
function addCatalogRow(text, code, httpNumber, meaning) {
  return insertSorted(text, {
    after: '| Code | HTTP | Meaning |',
    before: (line) => line.trim() === 'enum:',
    line: `        | \`${code}\` | ${httpNumber} | ${meaning} |`,
    sortKey: (line) => line.split('`')[1] ?? '',
  });
}

function addEnumValue(text, code) {
  return insertSorted(text, {
    after: '      enum:',
    before: (line) => line.trim() !== '' && !line.trim().startsWith('- '),
    line: `        - ${code}`,
    sortKey: (line) => line.replace('-', '').trim(),
  });
}

/**
 * The Java constant, with the meaning as its javadoc.
 *
 * Inserted before the final `;` constant rather than appended, because the enum is alphabetical and
 * the last entry carries the terminator — appending would produce two `;` and a compile error, which
 * is at least loud. Getting the order wrong would not be.
 */
function addJavaConstant(text, constant, code, status, meaning) {
  const lines = text.split('\n');
  const terminator = lines.findIndex((line) => /^    [A-Z][A-Z0-9_]*\(".*"\, HttpStatus\.[A-Z_]+\);$/.test(line));
  if (terminator === -1) {
    throw new Error('addJavaConstant: could not find the final enum constant in ApiErrorCode');
  }

  const block = [
    `    /** ${meaning}${meaning.endsWith('.') ? '' : '.'} */`,
    `    ${constant}("${code}", HttpStatus.${status}),`,
    '',
  ];

  // Alphabetical: find the first existing constant that sorts after this one.
  let target = terminator;
  for (let index = 0; index <= terminator; index += 1) {
    const match = /^    ([A-Z][A-Z0-9_]*)\("/.exec(lines[index]);
    if (match !== null && match[1] > constant) {
      // Step back over the javadoc that belongs to it, so the new block lands above the comment
      // rather than between a comment and the constant it documents.
      target = index;
      while (target > 0 && /^\s*(\*|\/\*)/.test(lines[target - 1])) {
        target -= 1;
      }
      break;
    }
  }

  if (target === terminator) {
    // Sorts last: the previous final constant has to give up its `;`.
    lines[terminator] = lines[terminator].replace(/;$/, ',');
    block[1] = block[1].replace(/,$/, ';');
    block.pop();
    lines.splice(terminator + 1, 0, '', ...block);
    return lines.join('\n');
  }

  lines.splice(target, 0, ...block);
  return lines.join('\n');
}

/**
 * The i18n string, keyed identically in both locales.
 *
 * The Malay entry is the English text prefixed `TODO(ms):`. That is deliberately ugly: it is visible
 * in review, it is greppable, and `common.errors.test.ts` — which asserts both locales carry every
 * registered code — passes, so the build is not blocked on a translation the generator has no
 * business inventing.
 */
function addLocaleString(relativePath, code, meaning) {
  const messages = readJson(relativePath);
  const sentence = meaning.endsWith('.') ? meaning : `${meaning}.`;
  const isMalay = relativePath.includes('/ms/');
  messages.errors = withSortedKeys({
    ...messages.errors,
    [code]: isMalay ? `TODO(ms): ${sentence}` : sentence,
  });
  return stringifyJson(messages);
}

/**
 * The runtime value list TypeScript uses to narrow an arbitrary server string.
 *
 * The step `errors.yaml`'s header forgot. Its omission is caught — `ERROR_CODE_LIST_IS_EXHAUSTIVE`
 * makes `npm run typecheck` fail — but only after the generated client has been regenerated, so an
 * author who followed the documented five steps gets a type error in a file they never touched.
 */
function addRuntimeCode(text, code) {
  return insertSorted(text, {
    after: 'export const REGISTERED_ERROR_CODES = [',
    before: (line) => line.startsWith('] as const'),
    line: `  '${code}',`,
    sortKey: (line) => line.replace(/[',]/g, '').trim(),
  });
}
