/**
 * Cross-platform dispatcher for `npm run prereq:test`.
 *
 * Same rationale as scripts/prereq.mjs: pick the test script that matches the host
 * shell rather than assuming bash is available (and correct) on Windows.
 *
 * On Windows both suites run when Git Bash is present, since the .sh script is a
 * supported target there per plans/superpower/PLAN.md 4.0.0. Git Bash is located
 * explicitly - `bash` on PATH is usually WSL, which is a different toolchain.
 */

import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { existsSync } from 'node:fs';

const testsDir = dirname(fileURLToPath(import.meta.url));
const isWindows = process.platform === 'win32';

const GIT_BASH_CANDIDATES = [
  'C:\\Program Files\\Git\\bin\\bash.exe',
  'C:\\Program Files (x86)\\Git\\bin\\bash.exe',
];

/** @returns {{name: string, command: string, args: string[]}[]} */
function suites() {
  const shSuite = (bash) => ({
    name: 'check-prerequisites.sh',
    command: bash,
    args: [join(testsDir, 'test-check-prerequisites.sh')],
  });

  if (!isWindows) return [shSuite('bash')];

  const found = [{
    name: 'check-prerequisites.ps1',
    command: 'powershell.exe',
    args: [
      '-NoProfile',
      '-ExecutionPolicy', 'Bypass',
      '-File', join(testsDir, 'check-prerequisites.tests.ps1'),
    ],
  }];

  const gitBash = GIT_BASH_CANDIDATES.find(existsSync);
  if (gitBash) {
    found.push(shSuite(gitBash));
  } else {
    console.log('\nSkipping check-prerequisites.sh suite: Git Bash not found.');
    console.log('  Looked in:', GIT_BASH_CANDIDATES.join(', '));
    console.log('  (`bash` on PATH is typically WSL, a different toolchain - not used here.)');
  }
  return found;
}

let failed = 0;
for (const suite of suites()) {
  console.log(`\n=== ${suite.name} ===`);
  const result = spawnSync(suite.command, suite.args, { stdio: 'inherit' });
  if (result.error) {
    console.error(`Could not run ${suite.name}: ${result.error.message}`);
    failed++;
    continue;
  }
  if (result.status !== 0) failed++;
}

if (failed > 0) {
  console.error(`\n${failed} suite(s) failed.\n`);
  process.exit(1);
}
console.log('\nAll prerequisite script suites passed.\n');
