/**
 * The three-stage verification ladder (review §6.C): `verify:fast`, `verify:task`, `verify:full`.
 *
 * **The gates are not weakened.** `verify:full` runs exactly what CI runs, and nothing may be merged
 * without it. What changes is when the expensive parts run. Today the only documented command is the
 * full one, so an agent fixing a one-line Checkstyle violation pays for Testcontainers, a Next.js
 * production build, and full-repository coverage to find out. That cost is not paid once — it is paid
 * on every iteration of the edit loop, which is where an agent spends nearly all of its time.
 *
 * | Stage    | Question it answers                        | Rough cost   |
 * |----------|--------------------------------------------|--------------|
 * | `fast`   | did I break what I just touched?           | seconds      |
 * | `task`   | is this task's Definition of Done met?     | a minute     |
 * | `full`   | would CI accept this?                      | minutes, Docker |
 *
 * `fast` is scoped by `git diff`: a backend-only change does not start Node, and a docs-only change
 * runs nothing but the stale-doc check. Scoping by the diff rather than by a flag is deliberate —
 * a flag is a thing an agent gets wrong in the direction of running less.
 *
 * Exit code is 0 only if every step it decided to run passed. A skipped step is reported as skipped
 * and never as passed: "verify:fast green" has to mean something, and a stage that silently ran
 * nothing is how a suite stops being believed.
 */

import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { repoPath, toPosix } from './lib/repo.mjs';

const STAGES = new Set(['fast', 'task', 'full']);
const args = process.argv.slice(2);
const stage = args[0];

if (!STAGES.has(stage)) {
  console.error('Usage: node scripts/verify.mjs <fast|task|full> [task id]');
  console.error('  npm run verify:fast');
  console.error('  npm run verify:task -- 18');
  console.error('  npm run verify:full');
  process.exit(2);
}

const isWindows = process.platform === 'win32';
// An absolute path, not `gradlew.bat`. With `shell: true` on Windows the wrapper's own directory
// is not on PATH, so the bare name fails with "not recognized as an internal or external command"
// even though cwd is right beside it — and `./gradlew` is not a thing cmd.exe understands.
const gradle = repoPath('apps', 'backend', isWindows ? 'gradlew.bat' : 'gradlew');
const npm = isWindows ? 'npm.cmd' : 'npm';

const changed = changedFiles();
const scope = {
  backend: stage !== 'fast' || changed.some((file) => file.startsWith('apps/backend/')),
  frontend: stage !== 'fast' || changed.some((file) => file.startsWith('apps/frontend/')),
  contract: stage !== 'fast' || changed.some((file) => file.endsWith('openapi.yaml') || file.endsWith('errors.yaml')),
  scripts: stage !== 'fast' || changed.some((file) => file.startsWith('scripts/')),
  docs: true,
};

if (stage === 'fast' && changed.length === 0) {
  console.log('verify:fast: no changes against the merge base — nothing to check.');
  process.exit(0);
}

printHeader();
const results = [];
for (const step of plan()) {
  results.push(run(step));
}
printSummary();

// -----------------------------------------------------------------------------------------------

function plan() {
  if (stage === 'fast') {
    return fastPlan();
  }
  if (stage === 'task') {
    return taskPlan();
  }
  return fullPlan();
}

/**
 * The edit loop. Compile, unit tests, lint, typecheck — nothing that starts a container or a
 * production bundler.
 *
 * Backend Checkstyle runs here rather than only at `task`: a style violation is the cheapest possible
 * failure to find and the most annoying one to discover after a green test run.
 */
function fastPlan() {
  const steps = [];
  if (scope.backend) {
    steps.push(backendStep('unit tests', ['test']));
    steps.push(backendStep('checkstyle', ['checkstyleMain', 'checkstyleTest']));
  }
  if (scope.frontend) {
    steps.push(frontendStep('typecheck', ['run', 'typecheck']));
    steps.push(frontendStep('lint', ['run', 'lint']));
    steps.push(frontendStep('unit tests', ['test']));
  }
  if (scope.contract) {
    steps.push(contractDriftStep());
  }
  if (scope.scripts) {
    steps.push(scriptTestsStep());
  }
  steps.push(staleDocsStep());
  return steps;
}

/**
 * Task completion. Everything `fast` runs, plus the gates that decide whether a Definition of Done
 * is actually met: coverage thresholds, the ArchUnit rules, formatting, and contract drift.
 *
 * Still no Docker. The Testcontainers suite is `full`'s job — it is the slowest thing in the
 * repository and it is not what tells an author whether their own task is finished.
 */
function taskPlan() {
  return [
    backendStep('build, checkstyle, ArchUnit, coverage', ['build', '-x', 'integrationTest']),
    frontendStep('format check', ['run', 'format:check']),
    frontendStep('lint', ['run', 'lint']),
    frontendStep('typecheck', ['run', 'typecheck']),
    frontendStep('tests and coverage thresholds', ['run', 'test:coverage']),
    contractDriftStep(),
    codeMapDriftStep(),
    scriptTestsStep(),
    staleDocsStep(),
  ];
}

/** What CI runs. The Docker smoke test is CI's own job and is not reproduced here. */
function fullPlan() {
  return [
    ...taskPlan(),
    backendStep('integration tests (Testcontainers — needs Docker)', ['integrationTest']),
    frontendStep('production build', ['run', 'build']),
  ];
}

function backendStep(label, gradleArgs) {
  return {
    label: `backend: ${label}`,
    command: gradle,
    args: ['--no-daemon', ...gradleArgs],
    cwd: repoPath('apps', 'backend'),
  };
}

function frontendStep(label, npmArgs) {
  return {
    label: `frontend: ${label}`,
    command: npm,
    args: npmArgs,
    cwd: repoPath('apps', 'frontend'),
    // A frontend step with no `node_modules` has not passed and has not failed; it did not run.
    // Reporting it as a pass is how "green" stops meaning anything.
    skipIf: () => (existsSync(repoPath('apps', 'frontend', 'node_modules')) ? null : 'node_modules is missing — run `npm ci` in apps/frontend'),
  };
}

/**
 * The OpenAPI drift gate, run the same way CI runs it: regenerate, then ask git whether anything
 * moved. Checking the generated file into the tree is what makes this possible at all.
 */
function contractDriftStep() {
  return {
    label: 'contract: codegen drift',
    command: npm,
    args: ['run', 'codegen'],
    cwd: repoPath('apps', 'frontend'),
    skipIf: () => (existsSync(repoPath('apps', 'frontend', 'node_modules')) ? null : 'node_modules is missing'),
    then: () => assertClean('apps/frontend/src/generated', 'npm run codegen'),
  };
}

function codeMapDriftStep() {
  return {
    label: 'docs: CODE-MAP is current',
    command: process.execPath,
    args: [repoPath('scripts', 'code-map.mjs'), '--check'],
    cwd: repoPath(),
  };
}

function staleDocsStep() {
  return {
    label: 'docs: no stale claims',
    command: process.execPath,
    args: [repoPath('scripts', 'stale-docs.mjs')],
    cwd: repoPath(),
  };
}

/**
 * The generators' own tests.
 *
 * They write files, which is exactly the kind of tool that has to be tested rather than trusted: a
 * broken generator does not fail loudly, it produces a slice with a subtly wrong registry entry that
 * somebody then debugs as an application bug. `node:test` ships with Node 22, so this costs no
 * dependency.
 */
function scriptTestsStep() {
  return {
    label: 'scripts: generator tests',
    command: process.execPath,
    args: ['--test', repoPath('scripts', 'tests', 'generate.test.mjs')],
    cwd: repoPath(),
  };
}

/**
 * A generated tree that changed is a drift failure, not a diff to commit.
 *
 * The message names the command that produced the change, because the fix is always "run it and
 * commit the result" and an agent that has to infer that will sometimes revert the file instead.
 *
 * <b>`git diff --quiet`, not `git status --porcelain`.</b> The first version used status, and it
 * reported drift on every Windows run: `.gitattributes` declares `* text=auto`, the committed blob is
 * LF, `npm run codegen` writes LF, and `status` flags the stat mismatch as a modification while
 * `git diff` correctly shows nothing. A gate that cries wolf on one platform is a gate that gets
 * ignored on both, which is worse than not having it.
 *
 * Untracked files are checked separately, because `diff` cannot see them — a generator that emits a
 * brand-new file would otherwise pass silently.
 */
function assertClean(path, command) {
  const tracked = spawnSync('git', ['diff', '--quiet', '--', path], { cwd: repoPath(), encoding: 'utf8' });
  const untracked = spawnSync('git', ['ls-files', '--others', '--exclude-standard', '--', path], {
    cwd: repoPath(),
    encoding: 'utf8',
  });
  const changed = (tracked.status ?? 0) !== 0 || (untracked.stdout ?? '').trim() !== '';
  if (!changed) {
    return null;
  }
  return `${path} changed after \`${command}\`. The committed output is stale — run it and commit the result.`;
}

/**
 * Spawns one step, correctly, on both platforms.
 *
 * Two Windows-only traps, and this repository walks into both because its checkout path contains a
 * space:
 *
 * 1. `npm.cmd` and `gradlew.bat` are batch files, and Node will not execute a `.cmd`/`.bat` directly
 *    — they need a shell.
 * 2. With `shell: true`, cmd.exe re-parses the command line, so a path containing a space splits into
 *    two tokens and fails with `'C:\Users\Voo' is not recognized`. Under a shell, anything containing
 *    a space has to be quoted.
 *
 * So the shell is enabled only for the commands that require it, and quoting is applied only when the
 * shell is in play — quoting without a shell passes the quote characters through as part of the
 * argument, which is its own confusing failure.
 */
function spawnStep(command, args, options) {
  const needsShell = isWindows && /\.(cmd|bat)$/i.test(command);
  const quote = (value) => (/\s/.test(value) ? `"${value}"` : value);
  return spawnSync(needsShell ? quote(command) : command, needsShell ? args.map(quote) : args, {
    ...options,
    shell: needsShell,
  });
}

function run(step) {
  const skipReason = step.skipIf?.() ?? null;
  if (skipReason !== null) {
    console.log(`\n── SKIP  ${step.label}\n   ${skipReason}`);
    return { label: step.label, outcome: 'skipped', detail: skipReason };
  }

  console.log(`\n── RUN   ${step.label}`);
  const result = spawnStep(step.command, step.args, { cwd: step.cwd, stdio: 'inherit' });

  if (result.error !== undefined) {
    return { label: step.label, outcome: 'failed', detail: result.error.message };
  }
  if ((result.status ?? 1) !== 0) {
    return { label: step.label, outcome: 'failed', detail: `exit code ${result.status}` };
  }
  const followUp = step.then?.() ?? null;
  if (followUp !== null) {
    return { label: step.label, outcome: 'failed', detail: followUp };
  }
  return { label: step.label, outcome: 'passed', detail: '' };
}

/**
 * Changed files against the merge base with the integration branch.
 *
 * Merge base rather than `HEAD~1`: what matters is everything this branch touched, not the last
 * commit. Falls back to the whole working tree when there is no `dev` to compare against — a shallow
 * CI checkout, a fresh clone — because scoping to nothing and reporting green is the one behaviour
 * this must never have.
 */
function changedFiles() {
  const base = spawnSync('git', ['merge-base', 'HEAD', 'dev'], { cwd: repoPath(), encoding: 'utf8' });
  const range = (base.status ?? 1) === 0 && base.stdout.trim() !== '' ? base.stdout.trim() : null;
  const diff = spawnSync('git', range === null ? ['status', '--porcelain'] : ['diff', '--name-only', range], {
    cwd: repoPath(),
    encoding: 'utf8',
  });
  if ((diff.status ?? 1) !== 0) {
    return [];
  }
  return diff.stdout
    .split('\n')
    .map((line) => (range === null ? line.slice(3) : line).trim())
    .filter((line) => line !== '')
    .map((line) => toPosix(repoPath(line)));
}

function printHeader() {
  console.log(`verify:${stage}`);
  if (stage === 'fast') {
    console.log(`  ${changed.length} changed file(s) against the merge base with dev`);
    console.log(`  scope: backend=${scope.backend} frontend=${scope.frontend} `
      + `contract=${scope.contract} scripts=${scope.scripts}`);
  }
}

function printSummary() {
  const failed = results.filter((result) => result.outcome === 'failed');
  const skipped = results.filter((result) => result.outcome === 'skipped');

  console.log(`\n${'─'.repeat(78)}`);
  for (const result of results) {
    console.log(`  ${result.outcome.toUpperCase().padEnd(8)}${result.label}${result.detail === '' ? '' : ` — ${result.detail}`}`);
  }
  console.log(
    `\nverify:${stage}: ${results.length - failed.length - skipped.length} passed, ` +
      `${failed.length} failed, ${skipped.length} skipped.`,
  );

  if (failed.length > 0) {
    process.exit(1);
  }
  if (skipped.length > 0 && stage !== 'fast') {
    // A completion gate that skipped a step has not completed. `fast` is allowed to skip by design;
    // `task` and `full` are the stages someone quotes as evidence, so they may not.
    console.error(`\nverify:${stage} cannot pass with skipped steps — resolve them and re-run.`);
    process.exit(1);
  }
  process.exit(0);
}
