/**
 * `npm run task:report -- 18` — completion evidence, captured rather than described (review §6.F).
 *
 * `docs/AGENT-HARNESS.md` §6 already requires real command output before a task may be marked
 * `done`. The gap it leaves is that the evidence is assembled by hand, which makes two failure modes
 * available: paraphrasing ("all tests pass") instead of quoting, and quoting a run from before the
 * last three commits. Both look identical in a PR.
 *
 * So this runs the commands itself, right now, in this working tree, and writes what they actually
 * printed to `artifacts/task-NN-evidence.md`. Nothing in the report is authored — every number in it
 * came out of a process this script started.
 *
 * ## What it will not do
 *
 * It does not edit `tasks/STATUS.md`. The review's §6.F suggests a `task:complete` that flips the
 * status once CI is green, and that is the right shape — but "CI is green for this commit" is a fact
 * only the CI provider can assert, and a local script that wrote `done` on the strength of a local
 * run would be manufacturing exactly the evidence the gate exists to demand. The report ends with
 * the ledger line to paste and the conditions under which pasting it is honest.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import {
  formatBytes,
  markdownSections,
  normaliseTaskId,
  parseStatusLedger,
  readText,
  repoPath,
  taskFile,
  toPosix,
} from './lib/repo.mjs';

const taskId = normaliseTaskId(process.argv.slice(2).find((arg) => !arg.startsWith('--')));
if (taskId === null) {
  console.error('Usage: npm run task:report -- <task id>      e.g. npm run task:report -- 18');
  process.exit(2);
}

const brief = taskFile(taskId);
if (brief === null) {
  console.error(`task:report: no tasks/${taskId}-*.md brief exists.`);
  process.exit(2);
}

const isWindows = process.platform === 'win32';
const outputDir = repoPath('artifacts');
const outputFile = repoPath('artifacts', `task-${taskId}-evidence.md`);

const commands = [
  { label: 'Backend — build, Checkstyle, ArchUnit, coverage', command: repoPath('apps', 'backend', isWindows ? 'gradlew.bat' : 'gradlew'), args: ['--no-daemon', 'build', '-x', 'integrationTest'], cwd: repoPath('apps', 'backend') },
  { label: 'Frontend — lint', command: isWindows ? 'npm.cmd' : 'npm', args: ['run', 'lint'], cwd: repoPath('apps', 'frontend') },
  { label: 'Frontend — typecheck', command: isWindows ? 'npm.cmd' : 'npm', args: ['run', 'typecheck'], cwd: repoPath('apps', 'frontend') },
  { label: 'Frontend — tests and coverage thresholds', command: isWindows ? 'npm.cmd' : 'npm', args: ['run', 'test:coverage'], cwd: repoPath('apps', 'frontend') },
  { label: 'Docs — CODE-MAP is current', command: process.execPath, args: [repoPath('scripts', 'code-map.mjs'), '--check'], cwd: repoPath() },
  { label: 'Docs — no stale claims', command: process.execPath, args: [repoPath('scripts', 'stale-docs.mjs')], cwd: repoPath() },
];

console.log(`task:report: capturing evidence for task ${taskId}. This runs the real gates.\n`);

const runs = commands.map((entry) => {
  console.log(`── ${entry.label}`);
  const result = spawnStep(entry.command, entry.args, {
    cwd: entry.cwd,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
  const exitCode = result.error !== undefined ? -1 : (result.status ?? -1);
  console.log(`   exit ${exitCode}`);
  return {
    ...entry,
    exitCode,
    output: result.error !== undefined ? result.error.message : `${result.stdout ?? ''}${result.stderr ?? ''}`,
  };
});

mkdirSync(outputDir, { recursive: true });
const report = renderReport();
writeFileSync(outputFile, report, 'utf8');

console.log(`\ntask:report: wrote ${toPosix(outputFile)} (${formatBytes(Buffer.byteLength(report))}).`);
process.exit(runs.every((run) => run.exitCode === 0) ? 0 : 1);

// -----------------------------------------------------------------------------------------------

/**
 * Spawns one command, correctly, on both platforms.
 *
 * `npm.cmd` and `gradlew.bat` need a shell because Node will not execute a batch file directly; under
 * that shell, a path containing a space has to be quoted or cmd.exe splits it. This repository's
 * checkout path contains a space, so both halves are load-bearing. See `verify.mjs` for the same
 * helper and the failure it produces when either half is missing.
 */
function spawnStep(command, args, options) {
  const needsShell = isWindows && /\.(cmd|bat)$/i.test(command);
  const quote = (value) => (/\s/.test(value) ? `"${value}"` : value);
  return spawnSync(needsShell ? quote(command) : command, needsShell ? args.map(quote) : args, {
    ...options,
    shell: needsShell,
  });
}

function renderReport() {
  const row = parseStatusLedger().find((entry) => entry.id === taskId);
  const sections = markdownSections(readText(brief));
  const passed = runs.filter((run) => run.exitCode === 0).length;

  return [
    `# Task ${taskId} — completion evidence`,
    '',
    `Produced by \`npm run task:report -- ${taskId}\`. Every command below was executed by that run;`,
    'the output is verbatim. Nothing here is a summary of a previous run.',
    '',
    '| | |',
    '|---|---|',
    `| Task | ${row?.title ?? taskId} (\`${toPosix(brief)}\`) |`,
    `| Ledger status at capture | \`${row?.status ?? 'unknown'}\` |`,
    `| Commit | \`${git(['rev-parse', 'HEAD'])}\` |`,
    `| Branch | \`${git(['rev-parse', '--abbrev-ref', 'HEAD'])}\` |`,
    `| Working tree | ${git(['status', '--porcelain']) === '' ? 'clean' : '**dirty — see the diff section**'} |`,
    `| Gates | ${passed}/${runs.length} passed |`,
    '',
    '## Definition of Done, from the brief',
    '',
    sections.get('Definition of Done') ?? '_The brief has no Definition of Done section._',
    '',
    '## Diff against `dev`',
    '',
    '```',
    git(['diff', '--stat', 'dev...HEAD']) || '(no commits ahead of dev)',
    '```',
    '',
    '## Gate output',
    '',
    ...runs.flatMap(renderRun),
    '## Marking this task `done`',
    '',
    passed === runs.length
      ? [
          'Every local gate passed. That is necessary and **not sufficient** —',
          '`docs/AGENT-HARNESS.md` §6 requires the PR\'s CI run to be green, because a local pass',
          'says nothing about the Testcontainers suite, the Docker smoke test, or Linux.',
          '',
          'Once CI is green on the PR, add this to `tasks/STATUS.md` and change the status cell:',
          '',
          '```',
          `| ${taskId} | … | … | \`done\` | Commit \`${git(['rev-parse', '--short', 'HEAD'])}\`, PR #NNN. <evidence summary>. |`,
          '```',
        ].join('\n')
      : [
          `**${runs.length - passed} gate(s) failed.** This task is not done, and no ledger change is`,
          'warranted. Read the output above — a failing gate is the cheapest description of what is',
          'left, and it is more specific than any summary of it would be.',
        ].join('\n'),
    '',
  ].join('\n');
}

/**
 * One command's output, tail-truncated.
 *
 * The tail rather than the head: Gradle and Vitest both print the verdict last, and a report that
 * kept the first 200 lines of a Gradle run would faithfully preserve the dependency resolution and
 * throw away the test count. 200 lines is enough for the summary plus a stack trace.
 */
function renderRun(run) {
  const lines = run.output.replace(/\r\n/g, '\n').trimEnd().split('\n');
  const truncated = lines.length > 200;
  return [
    `### ${run.exitCode === 0 ? 'PASS' : 'FAIL'} — ${run.label}`,
    '',
    `\`${run.command} ${run.args.map(shorten).join(' ')}\` in \`${toPosix(run.cwd) || '.'}\` → exit ${run.exitCode}`,
    '',
    '```',
    truncated ? `… ${lines.length - 200} earlier line(s) omitted …` : '',
    lines.slice(-200).join('\n'),
    '```',
    '',
  ];
}

/** Absolute paths in a committed report are noise and leak a machine layout. */
function shorten(argument) {
  return argument.startsWith(repoPath()) ? toPosix(argument) : argument;
}

function git(args) {
  const result = spawnSync('git', args, { cwd: repoPath(), encoding: 'utf8' });
  return (result.status ?? 1) === 0 ? result.stdout.trim() : '';
}
