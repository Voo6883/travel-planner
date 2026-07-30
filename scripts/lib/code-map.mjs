/**
 * Builds the code map: the questions an agent asks at the start of every task, answered once.
 *
 * The 2026-07-29 review's §6.E, and the reason it is worth a script rather than a document. Each
 * section below replaces a specific repository-wide grep that an agent currently runs from scratch:
 *
 * | Section       | The grep it replaces                                              |
 * |---------------|-------------------------------------------------------------------|
 * | `ports`       | "who implements `KnowledgePort`, and where does the adapter live?" |
 * | `errorCodes`  | "is `version_conflict` registered, and does the UI translate it?"  |
 * | `migrations`  | "what is the next free migration number?"                         |
 * | `features`    | "where is the controller / service / adapter for trips?"          |
 * | `tasks`       | "which tasks are `done`, and what is blocking 21?"                |
 *
 * Every value is derived from the source of truth, never mirrored. The next migration number comes
 * from the filenames, not from a note in a document that was accurate last Tuesday.
 *
 * **Deliberately not a parser.** These are regexes over Java and TypeScript, not an AST walk. The
 * output is a lookup table an agent uses to decide which three files to open — being wrong about an
 * unusual declaration costs one extra file read, while a real parser would cost a dependency the
 * root `package.json` is not allowed to have. Anything ambiguous is omitted rather than guessed:
 * a missing entry sends the agent to `grep`, an invented one sends it to the wrong file.
 */

import { join } from 'node:path';
import {
  allTaskFiles,
  markdownSections,
  parseStatusLedger,
  readText,
  repoPath,
  toPosix,
  walk,
} from './repo.mjs';

const BACKEND_MAIN = repoPath('apps', 'backend', 'src', 'main', 'java');
const MIGRATIONS = repoPath('apps', 'backend', 'src', 'main', 'resources', 'db', 'migration');
const FRONTEND_SRC = repoPath('apps', 'frontend', 'src');

export function buildCodeMap() {
  return {
    // No timestamp and no commit hash. The file is committed, so `git diff` has to be able to say
    // "nothing changed" — a generated header carrying the current time makes every run a diff and
    // turns the CI drift check into noise that gets switched off.
    generatedBy: 'npm run code-map',
    ports: mapPorts(),
    errorCodes: mapErrorCodes(),
    migrations: mapMigrations(),
    features: mapFeatures(),
    tasks: mapTasks(),
  };
}

/**
 * Every `domain/port/*Port.java` and the classes that implement it.
 *
 * Implementations are found by scanning for `implements …Port`, which catches the adapters and also
 * the test fakes — and the fakes are worth having. "What does an in-memory `RefreshTokenPort` look
 * like" is the question an agent asks when it adds a port method, and the answer is a file it would
 * otherwise not know exists.
 */
function mapPorts() {
  const ports = {};
  for (const file of walk(join(BACKEND_MAIN, 'com', 'travelplanner', 'domain', 'port'), /\.java$/)) {
    const name = file.split(/[\\/]/).pop().replace('.java', '');
    if (name === 'package-info') {
      continue;
    }
    ports[name] = { declaredIn: toPosix(file), implementations: [] };
  }

  for (const file of walk(repoPath('apps', 'backend', 'src'), /\.java$/)) {
    const text = readText(file);
    for (const match of text.matchAll(/implements\s+([A-Za-z0-9_,\s]+?)\s*\{/g)) {
      for (const candidate of match[1].split(',').map((value) => value.trim())) {
        if (Object.hasOwn(ports, candidate)) {
          ports[candidate].implementations.push(toPosix(file));
        }
      }
    }
  }

  for (const port of Object.values(ports)) {
    port.implementations = [...new Set(port.implementations)].sort();
  }
  return ports;
}

/**
 * The error catalog, joined to the locale files that translate it.
 *
 * The join is the useful part. A code registered in `ApiErrorCode` but absent from
 * `locales/en/common.json` reaches a user as a raw identifier like `version_conflict`, and nothing
 * in either language's build fails — the two files have no relationship a compiler can see. Listing
 * `translated: false` next to the code is how that becomes visible before it ships.
 */
function mapErrorCodes() {
  const enumFile = join(BACKEND_MAIN, 'com', 'travelplanner', 'api', 'error', 'ApiErrorCode.java');
  const text = readText(enumFile);
  const locales = loadLocaleStrings();
  const codes = {};

  for (const match of text.matchAll(/^\s{4}([A-Z][A-Z0-9_]*)\("([a-z0-9_]+)",\s*HttpStatus\.([A-Z_]+)\)/gm)) {
    const [, constant, wireCode, status] = match;
    codes[wireCode] = {
      constant,
      httpStatus: status,
      translated: locales.filter((locale) => locale.text.includes(`"${wireCode}"`)).map((locale) => locale.name),
    };
  }
  return { declaredIn: toPosix(enumFile), codes };
}

function loadLocaleStrings() {
  return walk(join(FRONTEND_SRC, 'locales'), /\.json$/).map((file) => ({
    name: toPosix(file),
    text: readText(file),
  }));
}

/**
 * The migration ledger, and the one number every schema task needs first.
 *
 * `nextFreeVersion` is `max + 1` over the filenames rather than a value recorded in a document. Two
 * agents were given the same "next free migration" note in STATUS.md at different times; the
 * filenames cannot disagree with themselves.
 */
function mapMigrations() {
  const applied = [];
  for (const file of walk(MIGRATIONS, /^V\d+__.*\.sql$/)) {
    const name = file.split(/[\\/]/).pop();
    const version = Number(/^V(\d+)__/.exec(name)[1]);
    const text = readText(file);
    applied.push({
      version,
      file: `apps/backend/src/main/resources/db/migration/${name}`,
      createsTables: [...text.matchAll(/CREATE TABLE\s+(?:IF NOT EXISTS\s+)?"?([a-z_]+)"?/gi)].map((m) => m[1]),
      altersTables: [...new Set([...text.matchAll(/ALTER TABLE\s+"?([a-z_]+)"?/gi)].map((m) => m[1]))],
    });
  }
  applied.sort((left, right) => left.version - right.version);

  const highest = applied.length === 0 ? 0 : applied.at(-1).version;
  return {
    nextFreeVersion: `V${highest + 1}`,
    // Gaps are worth reporting rather than tolerating: Flyway's version order is the apply order,
    // so a missing number usually means a migration was renamed after it had already run somewhere.
    gaps: Array.from({ length: highest }, (_unused, index) => index + 1)
      .filter((version) => !applied.some((migration) => migration.version === version))
      .map((version) => `V${version}`),
    applied,
  };
}

/**
 * Backend controller/service/adapter groupings, and the frontend feature directories.
 *
 * Grouped by the package segment under `application/` and `infrastructure/persistence/`, because
 * that is how this codebase already names a feature — `application/trip`, `application/chat`. The
 * frontend half lists `features/*` and the query-key namespaces, which is what a task touching one
 * feature needs to know it must not reach into another.
 */
function mapFeatures() {
  return {
    backend: groupBackendByFeature(),
    frontendFeatures: listFrontendFeatures(),
    frontendQueryKeys: listQueryKeyNamespaces(),
  };
}

function groupBackendByFeature() {
  const features = {};
  const roots = [
    ['controllers', join(BACKEND_MAIN, 'com', 'travelplanner', 'api', 'controller')],
    ['application', join(BACKEND_MAIN, 'com', 'travelplanner', 'application')],
    ['adapters', join(BACKEND_MAIN, 'com', 'travelplanner', 'infrastructure', 'persistence')],
  ];

  for (const [kind, root] of roots) {
    for (const file of walk(root, /\.java$/)) {
      const posix = toPosix(file);
      const name = posix.split('/').pop().replace('.java', '');
      if (name === 'package-info') {
        continue;
      }
      const feature = kind === 'controllers' ? featureOfController(name) : posix.split('/').at(-2);
      features[feature] ??= { controllers: [], application: [], adapters: [] };
      features[feature][kind].push(posix);
    }
  }
  return features;
}

/** `TripBriefController` → `trip`; the controller package is flat, so the name is the only signal. */
function featureOfController(className) {
  const stem = className.replace(/Controller$/, '').replace(/([a-z0-9])([A-Z])/g, '$1 $2');
  return stem.split(' ')[0].toLowerCase();
}

function listFrontendFeatures() {
  const root = join(FRONTEND_SRC, 'features');
  const features = {};
  for (const file of walk(root, /\.(ts|tsx)$/)) {
    const posix = toPosix(file);
    const feature = posix.split('/')[4];
    if (feature === undefined || posix.includes('.test.')) {
      continue;
    }
    features[feature] ??= [];
    features[feature].push(posix);
  }
  return features;
}

function listQueryKeyNamespaces() {
  const file = join(FRONTEND_SRC, 'lib', 'query', 'query-keys.ts');
  const text = readText(file);
  const body = /export const queryKeys = \{([\s\S]*?)\n\} as const;/.exec(text);
  if (body === null) {
    return { declaredIn: toPosix(file), namespaces: [] };
  }
  return {
    declaredIn: toPosix(file),
    namespaces: [...body[1].matchAll(/^ {2}([a-zA-Z]+):/gm)].map((match) => match[1]),
  };
}

/**
 * Every task, its ledger status, and — the part a planner actually wants — what still blocks it.
 *
 * `blockedBy` is the dependency ids that are not yet `done`. The review's finding #8 was that tasks
 * were started with open dependencies and the violation was noticed in hindsight; computing the list
 * makes it a fact anyone can check in one command instead of a table anyone can misread.
 */
function mapTasks() {
  const ledger = parseStatusLedger();
  const byId = new Map(ledger.map((row) => [row.id, row]));
  const tasks = {};

  for (const { id, file } of allTaskFiles()) {
    const row = byId.get(id);
    const sections = markdownSections(readText(file));
    tasks[id] = {
      title: row?.title ?? firstHeading(readText(file)),
      file: toPosix(file),
      status: row?.status ?? 'unknown',
      dependsOn: row?.dependsOn ?? [],
      blockedBy: (row?.dependsOn ?? []).filter((dependency) => byId.get(dependency)?.status !== 'done'),
      hasSections: [...sections.keys()],
    };
  }
  return tasks;
}

function firstHeading(text) {
  const match = /^#\s+(.*)$/m.exec(text);
  return match === null ? 'unknown' : match[1].trim();
}
