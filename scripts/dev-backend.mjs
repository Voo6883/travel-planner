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
  stdio: 'inherit',
  shell: isWin,
  env: process.env,
});

child.on('exit', (code) => process.exit(code ?? 1));

function applyHostDefaults() {
  if (!process.env.BACKEND_INTERNAL_URL) {
    process.env.BACKEND_INTERNAL_URL = 'http://localhost:8080';
  }
  if (!process.env.SPRING_DATASOURCE_URL?.includes('localhost')) {
    process.env.SPRING_DATASOURCE_URL =
      'jdbc:postgresql://localhost:5432/travel_planner';
  }
}
