/**
 * Shared read-only helpers for the agent-efficiency scripts.
 *
 * These scripts exist because of one finding in the 2026-07-29 review: an agent starting a task
 * re-reads 7,500+ lines of planning documents to answer questions the repository could have
 * answered in a few kilobytes. So everything here is about turning facts that are *already* in the
 * tree into a form small enough to put in a prompt.
 *
 * Three rules the whole `scripts/lib` follows:
 *
 * 1. **Read-only, always.** Nothing here writes to `apps/`. A tool that an agent runs before it
 *    understands the repository must not be able to change the repository.
 * 2. **Derive, never duplicate.** Every value is computed from the source of truth — the migration
 *    filenames, the enum constants, `tasks/STATUS.md`. A hand-maintained copy would be wrong within
 *    a week, and a confidently wrong context pack is worse than none.
 * 3. **No dependencies.** The root `package.json` has no `dependencies` and no `devDependencies`
 *    (PLAN §4.0.0: orchestration only), so this is Node's standard library and nothing else.
 */

import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

/** The repository root — `scripts/lib/` is two levels down, and that is the only assumption. */
export const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

export function repoPath(...parts) {
  return join(repoRoot, ...parts);
}

/** Repo-relative and forward-slashed, so output is identical on Windows and Linux. */
export function toPosix(absolutePath) {
  return relative(repoRoot, absolutePath).split(sep).join('/');
}

/**
 * Read a text file with line endings normalised to LF.
 *
 * `.gitattributes` declares `* text=auto`, so the same file is CRLF in a Windows working tree and
 * LF in CI. Every regex below counts on `\n`; normalising once here is why none of them have to
 * spell `\r?\n` and why none of them can be accidentally written without it.
 */
export function readText(absolutePath) {
  return readFileSync(absolutePath, 'utf8').replace(/\r\n/g, '\n');
}

export function readTextIfPresent(absolutePath) {
  return existsSync(absolutePath) ? readText(absolutePath) : null;
}

/** Every file under `dir` matching `pattern`, recursively. Skips the directories nobody indexes. */
export function walk(dir, pattern = /.*/) {
  const ignored = new Set(['node_modules', '.git', 'build', 'dist', '.next', '.gradle', 'coverage']);
  const found = [];
  const pending = [dir];

  while (pending.length > 0) {
    const current = pending.pop();
    if (!existsSync(current)) {
      continue;
    }
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      if (ignored.has(entry.name)) {
        continue;
      }
      const full = join(current, entry.name);
      if (entry.isDirectory()) {
        pending.push(full);
      } else if (pattern.test(entry.name)) {
        found.push(full);
      }
    }
  }
  return found.sort();
}

/**
 * Normalises a task id from whatever the caller typed.
 *
 * `21`, `"21"`, `"NN=21"`, and `"task-21"` all mean task 21, and `"7"` means `07`. Being generous
 * here costs six lines and removes a whole class of "the script said no such task" round trips.
 */
export function normaliseTaskId(raw) {
  const digits = String(raw ?? '').match(/\d{1,2}/);
  if (digits === null) {
    return null;
  }
  return digits[0].padStart(2, '0');
}

/** The `tasks/NN-*.md` brief for a task id, or `null`. */
export function taskFile(taskId) {
  const match = readdirSync(repoPath('tasks'))
    .filter((name) => name.startsWith(`${taskId}-`) && name.endsWith('.md'))
    .sort()[0];
  return match === undefined ? null : repoPath('tasks', match);
}

/** Every `tasks/NN-*.md` brief, as `{ id, file }`, ascending. */
export function allTaskFiles() {
  return readdirSync(repoPath('tasks'))
    .filter((name) => /^\d{2}-.*\.md$/.test(name))
    .sort()
    .map((name) => ({ id: name.slice(0, 2), file: repoPath('tasks', name) }));
}

/**
 * The `## Heading` sections of a markdown file, as a `Map` from heading text to body.
 *
 * Section-level rather than whole-file access is the point: `tasks/NN-*.md` briefs are long, and a
 * context pack wants Objective, Scope, Do not, and Definition of Done — not the branch-naming
 * convention at the bottom.
 */
export function markdownSections(text) {
  const sections = new Map();
  let heading = null;
  let body = [];

  for (const line of text.split('\n')) {
    const match = /^##\s+(.*)$/.exec(line);
    if (match === null) {
      body.push(line);
      continue;
    }
    if (heading !== null) {
      sections.set(heading, body.join('\n').trim());
    }
    heading = match[1].trim();
    body = [];
  }
  if (heading !== null) {
    sections.set(heading, body.join('\n').trim());
  }
  return sections;
}

/**
 * The `tasks/STATUS.md` ledger, parsed into rows.
 *
 * STATUS.md is a set of markdown tables with one shape: `| ID | Task | Depends on | Status | Notes |`.
 * Parsing it rather than keeping a second machine-readable copy is deliberate — the ledger is the
 * thing humans update in the same PR as the work, so anything derived has to come from it or the two
 * diverge silently.
 *
 * @returns {Array<{id: string, title: string, dependsOn: string[], status: string, notes: string}>}
 */
export function parseStatusLedger() {
  const text = readText(repoPath('tasks', 'STATUS.md'));
  const rows = [];

  for (const line of text.split('\n')) {
    if (!line.startsWith('|')) {
      continue;
    }
    const cells = line.split('|').slice(1, -1).map((cell) => cell.trim());
    if (cells.length < 5 || !/^\d{2}$/.test(cells[0])) {
      continue;
    }
    const [id, title, dependsOn, status, ...notes] = cells;
    rows.push({
      id,
      title: stripMarkdownLink(title),
      dependsOn: dependsOn === '—' || dependsOn === '' ? [] : expandDependencies(dependsOn),
      status: status.replace(/`/g, '').trim(),
      notes: notes.join(' | ').trim(),
    });
  }
  return rows;
}

/** `[Trip and TripBrief core](18-trip-brief-core.md)` → `Trip and TripBrief core`. */
function stripMarkdownLink(cell) {
  return cell.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1').trim();
}

/**
 * `02–14` and `02-14` are ranges, not two ids.
 *
 * Task 15's row genuinely depends on thirteen tasks and the ledger writes it as a range with an en
 * dash. Reading that as `["02", "14"]` would report task 15 as unblocked while eleven of its
 * dependencies were still open, which is exactly the mistake the dependency gate exists to prevent.
 */
function expandDependencies(cell) {
  const ids = [];
  for (const part of cell.split(',').map((value) => value.trim())) {
    const range = /^(\d{2})\s*[–-]\s*(\d{2})$/.exec(part);
    if (range === null) {
      const single = /\d{2}/.exec(part);
      if (single !== null) {
        ids.push(single[0]);
      }
      continue;
    }
    for (let id = Number(range[1]); id <= Number(range[2]); id += 1) {
      ids.push(String(id).padStart(2, '0'));
    }
  }
  return ids;
}

/** Human-readable byte size, for the "is this pack small enough to paste" line. */
export function formatBytes(bytes) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

/** True when `path` exists and is a directory — used to skip optional trees. */
export function isDirectory(path) {
  return existsSync(path) && statSync(path).isDirectory();
}
