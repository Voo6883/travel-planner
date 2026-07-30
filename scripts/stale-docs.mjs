/**
 * Fails the build when a document tells an agent something the repository has stopped being true
 * (review §6.H, and finding #7 — "AI context poisoning").
 *
 * The failure this exists for is not a typo. `README.md` and `AGENTS.md` still described the repo as
 * planning-only with "nothing to run" while `apps/backend` and `apps/frontend` held tens of thousands
 * of lines and a green pipeline. That is worse than an out-of-date document, because of who reads it:
 * an agent given "the repository has no application scaffold" as authoritative context will refuse to
 * run the build, or scaffold a second application beside the first, or ignore the extension points it
 * was told do not exist. Every one of those wastes a full task cycle before anyone notices.
 *
 * ## The rule each assertion has to satisfy
 *
 * A check earns its place only if a **machine can decide it**. "Is this paragraph still accurate" is
 * not checkable and would be a lint rule that gets suppressed; "does this file claim there is no
 * scaffold while `apps/backend/build.gradle.kts` exists" is a fact with a yes-or-no answer.
 *
 * So each assertion below pairs a *claim pattern* with a *contradicting condition*, and fires only
 * when both hold. That keeps false positives near zero, which is the property that decides whether a
 * gate like this survives its first busy week.
 */

import { existsSync } from 'node:fs';
import { readText, repoPath, toPosix, walk } from './lib/repo.mjs';

/**
 * Documents an agent is pointed at as authoritative context. Deliberately not every markdown file in
 * the tree: a task brief written in the past tense about work that was pending at the time is
 * history, and rewriting history to satisfy a linter is worse than leaving it.
 */
const AGENT_FACING = [
  'README.md',
  'AGENTS.md',
  'docs/AI-AGENT-WORKFLOW.md',
  'docs/AGENT-HARNESS.md',
  'tasks/README.md',
  'tasks/EXECUTION-BASELINE.md',
];

const ASSERTIONS = [
  {
    id: 'scaffold-exists',
    // Matches the specific claim, not the word "scaffold" — briefs legitimately discuss scaffolding.
    claim: /\b(no|without an?)\s+(application\s+)?scaffold\b|nothing to run|planning[- ]only|no application code/i,
    contradiction: () =>
      existsSync(repoPath('apps', 'backend', 'build.gradle.kts')) &&
      existsSync(repoPath('apps', 'frontend', 'package.json')),
    explain:
      'claims the repository has no application scaffold, but apps/backend/build.gradle.kts and ' +
      'apps/frontend/package.json both exist. An agent that believes this will refuse to run the ' +
      'build or scaffold a second application beside the first.',
  },
  {
    id: 'migration-number',
    // Any "next free migration is Vn" sentence. There is exactly one right answer and it is derivable.
    claim: /next\s+(free|available)\s+migration[^\n]*?\bV(\d+)\b/i,
    contradiction: (match) => `V${match[2]}` !== nextFreeMigration(),
    explain: () =>
      `names a next-free migration that is not ${nextFreeMigration()}. Two agents given different ` +
      'answers to this question will collide on a version number, and Flyway will refuse to start ' +
      'the second one. The filenames are the source of truth.',
  },
  {
    id: 'task-status',
    // `Task 17` / `task 17` followed by a status word within the same sentence.
    claim: /\btask\s+(\d{2})\b[^.\n]{0,80}?\b(is\s+)?(not[_ ]started|in[_ ]progress|done|complete)\b/gi,
    contradiction: (match) => {
      const claimed = match[3].toLowerCase().replace(' ', '_').replace('complete', 'done');
      const actual = statusOf(match[1]);
      return actual !== null && actual !== claimed;
    },
    explain: (match) =>
      `says task ${match[1]} is \`${match[3]}\`, but tasks/STATUS.md says \`${statusOf(match[1])}\`. ` +
      'STATUS.md is the single source of truth for completion; a second answer in prose is the one ' +
      'an agent reads first.',
  },
];

const failures = [];

for (const relativePath of AGENT_FACING) {
  const absolute = repoPath(...relativePath.split('/'));
  if (!existsSync(absolute)) {
    continue;
  }
  const text = readText(absolute);

  for (const assertion of ASSERTIONS) {
    for (const match of matchAll(text, assertion.claim)) {
      if (!assertion.contradiction(match)) {
        continue;
      }
      failures.push({
        file: relativePath,
        line: lineOf(text, match.index),
        id: assertion.id,
        quote: match[0].trim().replace(/\s+/g, ' ').slice(0, 120),
        explain: typeof assertion.explain === 'function' ? assertion.explain(match) : assertion.explain,
      });
    }
  }
}

if (failures.length === 0) {
  console.log(`stale-docs: ${AGENT_FACING.length} agent-facing documents checked, no stale claims.`);
  process.exit(0);
}

console.error(`stale-docs: ${failures.length} stale claim(s) in agent-facing documentation.\n`);
for (const failure of failures) {
  console.error(`${failure.file}:${failure.line}  [${failure.id}]`);
  console.error(`  "${failure.quote}"`);
  console.error(`  ${failure.explain}\n`);
}
console.error('These are read by agents as authoritative context. Fix the document, not this check.');
process.exit(1);

// -----------------------------------------------------------------------------------------------

/** `matchAll` for a pattern that may or may not be global, without mutating the caller's regex. */
function matchAll(text, pattern) {
  const flags = pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`;
  return [...text.matchAll(new RegExp(pattern.source, flags))];
}

function lineOf(text, index) {
  return text.slice(0, index).split('\n').length;
}

let cachedNextMigration = null;

function nextFreeMigration() {
  if (cachedNextMigration === null) {
    const versions = walk(
      repoPath('apps', 'backend', 'src', 'main', 'resources', 'db', 'migration'),
      /^V\d+__.*\.sql$/,
    ).map((file) => Number(/^V(\d+)__/.exec(toPosix(file).split('/').pop())[1]));
    cachedNextMigration = `V${versions.length === 0 ? 1 : Math.max(...versions) + 1}`;
  }
  return cachedNextMigration;
}

let cachedStatuses = null;

/**
 * A task's status from the ledger, or `null` when the id is not in it.
 *
 * Read with a local regex rather than through `parseStatusLedger`, because this script has to be able
 * to run when STATUS.md is the file being changed — a stricter parser that threw on a half-edited
 * table would block the very commit that fixes it.
 */
function statusOf(taskId) {
  if (cachedStatuses === null) {
    cachedStatuses = new Map();
    const text = readText(repoPath('tasks', 'STATUS.md'));
    for (const row of text.matchAll(/^\|\s*(\d{2})\s*\|[^|]*\|[^|]*\|\s*`?([a-z_]+)`?\s*\|/gm)) {
      cachedStatuses.set(row[1], row[2]);
    }
  }
  return cachedStatuses.get(taskId) ?? null;
}
