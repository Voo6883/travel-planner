/**
 * `npm run generate:migration -- <snake_case_name>`
 *
 * The version number is the whole point. Two branches that each read "next free migration: V21" from
 * a note in a document both write `V21__…`, and Flyway refuses to start the second deployment — a
 * failure that surfaces at deploy time, in whichever environment merged second, with a message about
 * a checksum rather than about a merge. Deriving the number from the filenames at the moment the file
 * is created is the only version of this that cannot be stale.
 *
 * It also seeds the header. `docs/QUALITY-GATES.md` and `MigrationContractTest` between them require
 * a surprising amount of a migration in this repository — `timestamptz` never bare `timestamp`,
 * `numeric` never `double precision` for money, `created_at`/`updated_at` on mutable tables, a CHECK
 * constraint mirroring any domain enum, and a header that says *why*. A blank file gets some of that
 * wrong every time; a template gets it wrong never, and the prompts are where an author is reminded
 * of the ones that do not apply.
 */

import { ChangeSet, printNextSteps } from '../generate-support.mjs';
import { repoPath, toPosix, walk } from '../repo.mjs';

const MIGRATIONS = 'apps/backend/src/main/resources/db/migration';

export const usage = 'generate:migration -- <snake_case_name>            e.g. create_itinerary_tables';

export function plan(args) {
  const [name] = args;

  if (name === undefined || !/^[a-z][a-z0-9]*(_[a-z0-9]+)*$/.test(name)) {
    return {
      error: `'${name}' must be snake_case. MigrationContractTest asserts every file matches `
        + 'V{n}__{snake_case}.sql, so a hyphen or a capital fails the build rather than the deploy.',
    };
  }

  const version = nextVersion();
  const relativePath = `${MIGRATIONS}/V${version}__${name}.sql`;

  const changes = new ChangeSet(`generate:migration — V${version} (derived from the filenames, not from a note)`);
  changes.create(relativePath, template(version, name));

  return {
    changes,
    onDone: () => printNextSteps([
      `Replace the WHY block. A migration whose header says what it does is redundant with the SQL; the header exists for what the SQL cannot say.`,
      'Delete the prompts that do not apply, and satisfy the ones that do. They are the assertions in MigrationContractTest, restated where you can act on them.',
      `Mirror any domain enum in a CHECK constraint, then add the pair to MigrationContractTest — otherwise a new constant fails an INSERT in production instead of the build.`,
      'If you added an entity, the column types must satisfy `ddl-auto: validate` — see RefreshTokenEntity for why a `char(64)` needs an explicit @JdbcTypeCode.',
      'Run `npm run verify:fast`, then `npm run verify:full` once Docker is up — only the Testcontainers suite proves it applies.',
    ]),
  };
}

/**
 * `max + 1` over the existing filenames.
 *
 * Filenames rather than `schema_history`: the number has to be derivable from a checkout with no
 * database, which is the situation an agent is in when it starts. It also cannot disagree with itself
 * the way two documents can.
 */
function nextVersion() {
  const versions = walk(repoPath(...MIGRATIONS.split('/')), /^V\d+__.*\.sql$/)
    .map((file) => Number(/^V(\d+)__/.exec(toPosix(file).split('/').pop())[1]));
  return versions.length === 0 ? 1 : Math.max(...versions) + 1;
}

function template(version, name) {
  const title = name.replace(/_/g, ' ');
  return `-- V${version} — ${title} (tasks/NN-*.md, PLAN §8).
--
-- WHY THIS EXISTS
--
-- Replace this block. It is not a description of the statements below — those describe themselves.
-- It is for what they cannot say: which shapes were considered and rejected, which constraint is
-- load-bearing rather than defensive, and what breaks if somebody "simplifies" it later. The headers
-- of V19 and V21 are the reference for the level of detail this repository expects.
--
-- PROMPTS — delete the ones that do not apply, satisfy the ones that do. Each is an assertion in
-- MigrationContractTest or docs/QUALITY-GATES.md, restated where you can still act on it.
--
--   [ ] Every timestamp column is \`timestamptz\`. A bare \`timestamp\` means whatever the writing
--       session's timezone was, which is a value nobody can interpret afterwards.
--   [ ] Money is \`numeric\`, never \`double precision\`/\`real\`/\`float\`. Binary floating point
--       cannot represent 4000.10, and a million of them drift by an amount somebody has to explain.
--   [ ] A mutable table carries \`created_at\` and \`updated_at\`.
--   [ ] Every column mirroring a domain enum has a CHECK constraint listing exactly its constants,
--       and MigrationContractTest asserts the two agree. Without that, a new constant fails an
--       INSERT in production rather than failing the build.
--   [ ] A user-owned table is readable filtered by a \`user_id\` it can reach without a join
--       (PLAN §4.0.2-L), and has the index to serve that read.
--   [ ] Optimistic locking: a \`version\` column only for the aggregates ADR 008 §1 lists. Appending
--       rows is not one of them — a @Version there turns concurrent appends into a 409 nobody earned.
--   [ ] Nothing is NOT NULL that a legitimately-incomplete record would leave empty. PLAN §4.1.3
--       answers incompleteness with typed clarification, not with a rejected save.
--   [ ] No secret, credential, or seeded password appears in this file.

-- --------------------------------------------------------------------------------------------
-- <table_name> — one sentence on what it holds and at what grain.
-- --------------------------------------------------------------------------------------------
-- CREATE TABLE <table_name> (
--     id          uuid        PRIMARY KEY,
--
--     created_at  timestamptz NOT NULL DEFAULT now(),
--     updated_at  timestamptz NOT NULL DEFAULT now()
-- );

-- COMMENT ON TABLE <table_name> IS
--     'tasks/NN — what a reader of \\d needs to know that the column names do not say.';
`;
}
