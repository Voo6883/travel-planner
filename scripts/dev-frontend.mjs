/**
 * Next.js dev server with HMR (PLAN §4.0.0 hybrid mode).
 *
 * Cross-platform: uses npm.cmd on Windows.
 * Loads root `.env` when present so BACKEND_INTERNAL_URL reaches next.config rewrites.
 */

import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { loadEnvFile } from 'node:process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const frontendDir = path.join(root, 'apps', 'frontend');
const envPath = path.join(root, '.env');
const isWin = process.platform === 'win32';

if (existsSync(envPath)) {
  loadEnvFile(envPath);
} else {
  console.warn('[dev:frontend] No .env — copy .env.example to .env for local config.');
}

if (!process.env.BACKEND_INTERNAL_URL) {
  process.env.BACKEND_INTERNAL_URL = 'http://localhost:8080';
}

const npm = isWin ? 'npm.cmd' : 'npm';
const child = spawn(npm, ['run', 'dev'], {
  cwd: frontendDir,
  stdio: 'inherit',
  shell: isWin,
  env: process.env,
});

child.on('exit', (code) => process.exit(code ?? 1));
