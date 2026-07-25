/**
 * Cross-platform dispatcher for `npm run prereq`.
 *
 * Why this exists: npm runs scripts through cmd.exe on Windows, which cannot execute
 * check-prerequisites.sh. Routing through `bash` is not a fix either - on a typical
 * Windows box `bash.exe` resolves to WSL, which would report the Linux toolchain
 * instead of the Windows one and produce a confidently wrong PASS.
 *
 * So: Windows -> check-prerequisites.ps1, everything else -> check-prerequisites.sh.
 * Both scripts remain directly runnable, exactly as documented in
 * plans/superpower/PLAN.md 4.0.0.
 *
 * This file is orchestration only - all check logic lives in the two shell scripts.
 */

import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const scriptsDir = dirname(fileURLToPath(import.meta.url));
const isWindows = process.platform === 'win32';

const [command, args] = isWindows
  ? ['powershell.exe', [
      '-NoProfile',
      '-ExecutionPolicy', 'Bypass',
      '-File', join(scriptsDir, 'check-prerequisites.ps1'),
    ]]
  : ['bash', [join(scriptsDir, 'check-prerequisites.sh')]];

const result = spawnSync(command, args, { stdio: 'inherit' });

if (result.error) {
  console.error(`\nCould not run the prerequisite check: ${result.error.message}`);
  console.error(`Tried: ${command} ${args.join(' ')}\n`);
  process.exit(1);
}

process.exit(result.status ?? 1);
