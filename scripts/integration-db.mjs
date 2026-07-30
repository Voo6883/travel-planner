/**
 * `npm run test:integration` — the Testcontainers suite, against a locally-installed PostgreSQL.
 *
 * <b>Why this exists.</b> The integration suite is the only thing that proves a migration actually
 * applies, that an entity's column types satisfy `ddl-auto: validate`, and that a conditional
 * `UPDATE` really serialises two writers. It needs Docker, and Docker Desktop does not work on every
 * machine — `docs/HANDOFF-REMAINING-WORK.md` §4.8 records the specific failure on this project's. The
 * result was that migrations shipped with "compiles but was not applied" attached to them, which is a
 * caveat nobody can act on later.
 *
 * A locally-installed PostgreSQL is right there. This script uses it.
 *
 * <b>What it guarantees, and what it cannot.</b> It creates a **throwaway database per run** and drops
 * it afterwards, so the suite meets a genuinely empty schema — the property that makes "migrations
 * apply from nothing" a real assertion — and cannot touch anything a developer cares about. What it
 * cannot guarantee is the *version*: a container pins PostgreSQL 16 and pgvector, a local install is
 * whatever is installed. So it prints both and refuses outright when pgvector is missing, because V1's
 * `CREATE EXTENSION vector` would otherwise fail with an error about a file rather than about a
 * prerequisite.
 *
 * CI keeps using Testcontainers. This is the local fallback, not a replacement — an unrun test is
 * worth less than one run against a database somebody prepared deliberately.
 */

import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { repoPath } from './lib/repo.mjs';

const isWindows = process.platform === 'win32';

/** Where a Windows installer puts psql. On Unix it is expected on PATH. */
const WINDOWS_PSQL_ROOTS = ['C:\\Program Files\\PostgreSQL', 'D:\\PostgreSQL'];

const config = {
  host: process.env.PGHOST ?? 'localhost',
  port: process.env.PGPORT ?? '5432',
  user: process.env.PGUSER ?? 'postgres',
  password: process.env.PGPASSWORD ?? 'admin',
  // One database per run, named so a leaked one is obviously disposable and obviously ours.
  database: `travel_planner_it_${process.pid}`,
};

const passThrough = process.argv.slice(2);
const psql = findPsql();

if (psql === null) {
  fail([
    'Could not find `psql`.',
    '',
    isWindows
      ? `Looked on PATH and under ${WINDOWS_PSQL_ROOTS.join(', ')}.`
      : 'Looked on PATH.',
    '',
    'Install PostgreSQL 16 with pgvector — `scripts/install-postgres-windows.ps1` and',
    '`scripts/install-pgvector-windows.ps1` do it on Windows — or start Docker and run',
    '`npm run verify:full`, which uses Testcontainers instead.',
  ]);
}

console.log(`test:integration: psql at ${psql}`);
requireServer();
requirePgvector();

console.log(`test:integration: creating throwaway database ${config.database}`);
runPsql('postgres', `CREATE DATABASE "${config.database}"`);

let exitCode = 1;
try {
  // pgvector is per-database, and V1's CREATE EXTENSION needs the privilege to install it. Creating
  // it up front means a failure here names the extension rather than surfacing inside Flyway.
  runPsql(config.database, 'CREATE EXTENSION IF NOT EXISTS vector');
  exitCode = runSuite();
} finally {
  // Always dropped, even on failure. A leaked per-run database is how somebody ends up with forty of
  // them and no idea which one a failure came from. Terminate first: Spring's pool may still hold
  // connections, and DROP DATABASE fails while any remain.
  console.log(`\ntest:integration: dropping ${config.database}`);
  runPsql('postgres',
      `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${config.database}'`,
      { allowFailure: true });
  runPsql('postgres', `DROP DATABASE IF EXISTS "${config.database}"`, { allowFailure: true });
}

process.exit(exitCode);

// -----------------------------------------------------------------------------------------------

/**
 * Runs Gradle's `integrationTest` with the datasource pointed at the throwaway database.
 *
 * `AbstractPostgresIntegrationTest` reads these three variables and skips constructing the container
 * entirely when the URL is set — so on a machine with no Docker, nothing waits for a daemon.
 */
function runSuite() {
  const gradle = repoPath('apps', 'backend', isWindows ? 'gradlew.bat' : 'gradlew');
  const url = `jdbc:postgresql://${config.host}:${config.port}/${config.database}`;
  console.log(`test:integration: running the suite against ${url}\n`);

  const result = spawnStep(gradle, ['--no-daemon', 'integrationTest', ...passThrough], {
    cwd: repoPath('apps', 'backend'),
    stdio: 'inherit',
    env: {
      ...process.env,
      INTEGRATION_TEST_JDBC_URL: url,
      INTEGRATION_TEST_USERNAME: config.user,
      INTEGRATION_TEST_PASSWORD: config.password,
    },
  });
  return result.status ?? 1;
}

function requireServer() {
  const version = capture('postgres', 'SELECT version()');
  if (version === null) {
    fail([
      `Could not connect to PostgreSQL at ${config.host}:${config.port} as ${config.user}.`,
      '',
      'Override with PGHOST / PGPORT / PGUSER / PGPASSWORD. On Windows the service is',
      '`postgresql-x64-16`; check it with `Get-Service postgresql*`.',
    ]);
  }
  console.log(`test:integration: ${version.split(',')[0]}`);
}

/**
 * pgvector is not optional.
 *
 * V1 runs `CREATE EXTENSION IF NOT EXISTS vector`, and ADR 010 §5 stores embeddings in a `vector`
 * column. Without the extension the whole suite fails inside Flyway with a message about a missing
 * control file, which reads as a database problem rather than a missing prerequisite — the exact
 * confusion `AbstractPostgresIntegrationTest` chose a pgvector image to avoid.
 */
function requirePgvector() {
  const available = capture('postgres',
      "SELECT default_version FROM pg_available_extensions WHERE name = 'vector'");
  if (available === null || available === '') {
    fail([
      'This PostgreSQL has no `vector` extension available.',
      '',
      'V1 runs `CREATE EXTENSION IF NOT EXISTS vector` and ADR 010 §5 stores embeddings in a',
      '`vector` column, so the suite cannot run without it. Install it with',
      '`scripts/install-pgvector-windows.ps1`, or start Docker and use `npm run verify:full`.',
    ]);
  }
  console.log(`test:integration: pgvector ${available} available`);
}

function runPsql(database, sql, { allowFailure = false } = {}) {
  const result = spawnStep(psql, [...connectionArgs(database), '-v', 'ON_ERROR_STOP=1', '-c', sql], {
    encoding: 'utf8',
    env: { ...process.env, PGPASSWORD: config.password },
  });
  if ((result.status ?? 1) !== 0 && !allowFailure) {
    fail([`psql failed on ${database}:`, '', sql, '', (result.stderr ?? '').trim()]);
  }
  return result;
}

/** One scalar, or `null` when the query could not run. */
function capture(database, sql) {
  const result = spawnStep(psql, [...connectionArgs(database), '-tAc', sql], {
    encoding: 'utf8',
    env: { ...process.env, PGPASSWORD: config.password },
  });
  return (result.status ?? 1) === 0 ? (result.stdout ?? '').trim() : null;
}

function connectionArgs(database) {
  return ['-h', config.host, '-p', config.port, '-U', config.user, '-d', database];
}

/**
 * Locates `psql`: PATH first, then the usual Windows install roots, newest major version first.
 *
 * Searching rather than requiring PATH because the Windows installer does not add itself, and
 * "install PostgreSQL then also edit your PATH" is a step that gets skipped and then reported as
 * "the script does not work".
 */
function findPsql() {
  const onPath = spawnStep(isWindows ? 'where' : 'which', ['psql'], { encoding: 'utf8' });
  if ((onPath.status ?? 1) === 0) {
    const first = (onPath.stdout ?? '').split('\n')[0].trim();
    if (first !== '') {
      return first;
    }
  }
  if (!isWindows) {
    return null;
  }
  for (const root of WINDOWS_PSQL_ROOTS) {
    if (!existsSync(root)) {
      continue;
    }
    const versions = readdirSync(root)
        .filter((name) => /^\d+$/.test(name))
        .sort((left, right) => Number(right) - Number(left));
    for (const version of versions) {
      const candidate = join(root, version, 'bin', 'psql.exe');
      if (existsSync(candidate)) {
        return candidate;
      }
    }
  }
  return null;
}

/**
 * Spawns correctly on both platforms — the same two Windows traps `verify.mjs` documents: a `.cmd`
 * or `.bat` needs a shell, and under that shell a path containing a space has to be quoted.
 */
function spawnStep(command, args, options) {
  const needsShell = isWindows && /\.(cmd|bat)$/i.test(command);
  const quote = (value) => (/\s/.test(value) ? `"${value}"` : value);
  return spawnSync(needsShell ? quote(command) : command, needsShell ? args.map(quote) : args, {
    ...options,
    shell: needsShell,
  });
}

function fail(lines) {
  console.error(`\ntest:integration: ${lines.join('\n')}\n`);
  process.exit(1);
}
