/**
 * Host-run Spring Boot with DevTools + Gradle continuous rebuild.
 *
 * Cross-platform: uses gradlew.bat on Windows, ./gradlew elsewhere.
 * Loads root `.env` when present (PLAN §4.0.0.2).
 */

import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { loadEnvFile } from 'node:process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const backendDir = path.join(root, 'apps', 'backend');
const envPath = path.join(root, '.env');
const isWin = process.platform === 'win32';

if (existsSync(envPath)) {
  loadEnvFile(envPath);
} else {
  console.warn('[dev:backend] No .env — copy .env.example to .env for local config.');
}

applyHostDefaults();

const gradlew = isWin ? 'gradlew.bat' : './gradlew';
const child = spawn(gradlew, ['bootRun', '--continuous', '-x', 'test'], {
  cwd: backendDir,
  // stdout/stderr are piped rather than inherited so startup failures can be detected; see
  // `watch()` below. stdin stays inherited so `--continuous` still reads "ctrl-d then enter".
  stdio: ['inherit', 'pipe', 'pipe'],
  shell: isWin,
  env: process.env,
});

watch(child.stdout, process.stdout);
watch(child.stderr, process.stderr);

child.on('exit', (code) => process.exit(code ?? 1));

/**
 * Surfacing a dead backend.
 *
 * `bootRun --continuous` treats an application that failed to start as a *completed* task: Gradle
 * prints `BUILD SUCCESSFUL` and settles into `Waiting for changes to input files`, exit code 0.
 * Nothing downstream can tell that apart from a healthy run — dev-apps.mjs only tears down on a
 * non-zero exit, which never arrives — so the session sits there with a frontend serving :3000
 * against a backend that is not listening.
 *
 * The fix is a banner, not an exit. `--continuous` exists to stay up across edits, and killing the
 * whole dev session on a transient failure (Postgres still booting) would be worse than the
 * problem. The banner is deferred until Gradle prints the misleading idle line, so it lands *after*
 * the stack trace rather than being scrolled away by it.
 */
const FAILED_TO_START = /APPLICATION FAILED TO START|Application run failed|Error starting ApplicationContext/;
const STARTED = /Started TravelPlannerApplication/;
const REBUILDING = /Change detected, executing build/;
const GRADLE_IDLE = /Waiting for changes to input files/;

let startupFailed = false;

function watch(source, sink) {
  let pending = '';

  source.on('data', (chunk) => {
    // Raw pass-through first, so Gradle's progress bar and colours survive untouched.
    sink.write(chunk);

    const lines = (pending + chunk.toString()).split(/\r?\n/);
    // The last element is whatever came before the final newline - hold it until the rest arrives.
    pending = lines.pop() ?? '';

    for (const line of lines) {
      if (STARTED.test(line) || REBUILDING.test(line)) {
        startupFailed = false;
      } else if (FAILED_TO_START.test(line)) {
        startupFailed = true;
      } else if (startupFailed && GRADLE_IDLE.test(line)) {
        startupFailed = false;
        announceDeadBackend();
      }
    }
  });
}

function announceDeadBackend() {
  const port = process.env.SERVER_PORT ?? '8080';
  const rule = '='.repeat(78);
  process.stderr.write(
    `\n${rule}\n` +
      '  BACKEND IS NOT RUNNING\n\n' +
      `  Spring Boot failed to start. Gradle reports BUILD SUCCESSFUL anyway, because\n` +
      `  --continuous treats the crashed application as a finished task.\n\n` +
      `  Nothing is listening on :${port}. The frontend will still serve, but every\n` +
      `  /api/v1 request it proxies will fail.\n\n` +
      '  The cause is in the stack trace above. Fix it, then save any file under\n' +
      '  apps/backend/src to trigger a rebuild — or restart `npm run dev`.\n' +
      `${rule}\n\n`,
  );
}

function applyHostDefaults() {
  if (!process.env.BACKEND_INTERNAL_URL) {
    process.env.BACKEND_INTERNAL_URL = 'http://localhost:8080';
  }
  if (!process.env.SPRING_DATASOURCE_URL?.includes('localhost')) {
    process.env.SPRING_DATASOURCE_URL =
      'jdbc:postgresql://localhost:5432/travel_planner';
  }
}
