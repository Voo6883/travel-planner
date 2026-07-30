/**
 * Shared plumbing for the vertical-slice generators (review §6.D).
 *
 * Three properties everything here depends on:
 *
 * 1. **Never overwrite.** A generator that clobbers a file an agent has already edited turns a
 *    convenience into a data-loss bug. Every write refuses an existing path and says so.
 * 2. **Dry-run by default is wrong here, but a plan is not.** Each generator prints the full file
 *    list before writing, and `--dry-run` stops after printing. An agent that cannot see what a
 *    command is about to do will run it and then read the diff, which is slower than reading a plan.
 * 3. **All-or-nothing.** Every path is checked before the first byte is written. A half-generated
 *    slice — controller present, service missing — is worse than none: it compiles-ish, and the gap
 *    is somewhere in a diff rather than in an error message.
 *
 * <b>Skeletons only.</b> The review is explicit: "生成器只产 skeleton；业务逻辑仍由 Agent 实现."
 * What these remove is the mechanical error — a wrong package line, a mapper that was never
 * registered, an error code added to the enum and not to the locale files, a migration that picked a
 * version another branch already took. Not the thinking.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { formatBytes, readText, repoPath, toPosix } from './repo.mjs';

/** `research-job` → `ResearchJob`. The only naming rule the generators need. */
export function toPascalCase(slug) {
  return slug
    .split(/[-_\s]+/)
    .filter((part) => part !== '')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('');
}

/** `research-job` → `researchJob`. */
export function toCamelCase(slug) {
  const pascal = toPascalCase(slug);
  return pascal.charAt(0).toLowerCase() + pascal.slice(1);
}

/** `research-job` → `researchjob`. Java packages are lower-case with no separators. */
export function toJavaPackageSegment(slug) {
  return slug.replace(/[-_\s]+/g, '').toLowerCase();
}

/** `RESEARCH_JOB` from `research-job`, for an enum constant. */
export function toScreamingSnakeCase(slug) {
  return slug.replace(/[-\s]+/g, '_').toUpperCase();
}

/** `research_job` from `research-job` or `researchJob`, for a wire code or a column. */
export function toSnakeCase(value) {
  return value
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[-\s]+/g, '_')
    .toLowerCase();
}

/**
 * A lower-kebab slug, or `null` with the reason.
 *
 * Validated rather than normalised. `Research Job` almost certainly means `research-job`, but
 * silently rewriting the argument means the agent's next command — which will use whatever it typed —
 * addresses a slice that does not exist under that name.
 */
export function validateSlug(raw) {
  if (typeof raw !== 'string' || raw.trim() === '') {
    return { slug: null, error: 'a name is required' };
  }
  const slug = raw.trim();
  if (!/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/.test(slug)) {
    return {
      slug: null,
      error: `'${slug}' must be lower-kebab-case: letters and digits, single hyphens, starting with a letter`,
    };
  }
  return { slug, error: null };
}

/**
 * A planned change: either a new file or an edit to an existing one.
 *
 * Both go through the same list so the plan an agent reads is complete. A generator that printed its
 * new files and quietly also edited three registries would be the most confusing possible outcome —
 * the diff would contain changes the command never mentioned.
 */
export class ChangeSet {
  constructor(label) {
    this.label = label;
    this.creations = [];
    this.edits = [];
  }

  /** @param relativePath repo-relative, forward slashes */
  create(relativePath, contents) {
    this.creations.push({ relativePath, contents });
    return this;
  }

  /**
   * Registers an edit as a pure function of the current text.
   *
   * A function rather than a finished string, because several generators edit the same file and each
   * has to see the previous one's result. Passing pre-rendered text would make the order matter
   * silently.
   */
  edit(relativePath, transform, describe) {
    this.edits.push({ relativePath, transform, describe });
    return this;
  }

  print() {
    console.log(`${this.label}\n`);
    for (const { relativePath } of this.creations) {
      console.log(`  create  ${relativePath}`);
    }
    for (const { relativePath, describe } of this.edits) {
      console.log(`  edit    ${relativePath}${describe === undefined ? '' : `  — ${describe}`}`);
    }
    console.log('');
  }

  /**
   * Refuses the whole change set if anything is already there.
   *
   * Checked for every path before the first write, which is what makes a failed generation leave the
   * tree exactly as it was. Reporting all collisions rather than the first also matters: an agent that
   * has to re-run to discover the second conflict will assume the first fix was the only one.
   */
  assertSafe() {
    const collisions = this.creations
      .map(({ relativePath }) => relativePath)
      .filter((relativePath) => existsSync(repoPath(...relativePath.split('/'))));
    const missing = this.edits
      .map(({ relativePath }) => relativePath)
      .filter((relativePath) => !existsSync(repoPath(...relativePath.split('/'))));

    if (collisions.length === 0 && missing.length === 0) {
      return null;
    }
    const lines = [];
    if (collisions.length > 0) {
      lines.push('These files already exist. Nothing was written — a generator that overwrote your');
      lines.push('work would be a data-loss bug rather than a convenience:');
      lines.push(...collisions.map((path) => `  ${path}`));
    }
    if (missing.length > 0) {
      lines.push('These files were expected and are missing, so the registry edits cannot be applied:');
      lines.push(...missing.map((path) => `  ${path}`));
    }
    return lines.join('\n');
  }

  apply() {
    for (const { relativePath, contents } of this.creations) {
      const absolute = repoPath(...relativePath.split('/'));
      mkdirSync(dirname(absolute), { recursive: true });
      writeFileSync(absolute, contents, 'utf8');
      console.log(`  wrote   ${relativePath} (${formatBytes(Buffer.byteLength(contents))})`);
    }
    for (const { relativePath, transform } of this.edits) {
      const absolute = repoPath(...relativePath.split('/'));
      const before = readText(absolute);
      const after = transform(before);
      if (after === before) {
        console.log(`  skip    ${relativePath} (already up to date)`);
        continue;
      }
      writeFileSync(absolute, after, 'utf8');
      console.log(`  edited  ${relativePath}`);
    }
  }
}

/**
 * Inserts a line into an alphabetically-sorted block, in place.
 *
 * Used for the error enum, the locale maps, and the barrel exports — all three are sorted, all three
 * are the kind of list a hand edit appends to the bottom of, and a sorted list with one entry at the
 * end is how a merge conflict becomes a duplicate.
 *
 * @returns the new text, or the original when `line` is already present
 */
export function insertSorted(text, { after, before, line, sortKey }) {
  if (text.includes(line.trim())) {
    return text;
  }
  const lines = text.split('\n');
  const start = lines.findIndex((candidate) => candidate.includes(after));
  if (start === -1) {
    throw new Error(`insertSorted: could not find the opening marker ${JSON.stringify(after)}`);
  }
  let end = lines.length;
  for (let index = start + 1; index < lines.length; index += 1) {
    if (before(lines[index])) {
      end = index;
      break;
    }
  }
  const key = sortKey(line);
  let target = end;
  for (let index = start + 1; index < end; index += 1) {
    if (lines[index].trim() !== '' && sortKey(lines[index]) > key) {
      target = index;
      break;
    }
  }
  lines.splice(target, 0, line);
  return lines.join('\n');
}

/** Reads a JSON file preserving key order, so an insert can be placed rather than appended. */
export function readJson(relativePath) {
  return JSON.parse(readFileSync(repoPath(...relativePath.split('/')), 'utf8'));
}

/** Two-space JSON with a trailing newline — what Prettier produces for these files. */
export function stringifyJson(value) {
  return `${JSON.stringify(value, null, 2)}\n`;
}

/** Sorts an object's keys, for the locale error maps that are maintained alphabetically. */
export function withSortedKeys(object) {
  return Object.fromEntries(Object.entries(object).sort(([left], [right]) => left.localeCompare(right)));
}

/** The standard tail every generator prints: what to do with what it just made. */
export function printNextSteps(steps) {
  console.log('\nNext:');
  for (const [index, step] of steps.entries()) {
    console.log(`  ${index + 1}. ${step}`);
  }
  console.log('');
}

export { toPosix };
