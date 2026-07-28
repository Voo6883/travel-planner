/**
 * Runs backend + frontend dev servers together.
 *
 * Pure Node orchestration — no concurrently binary, so Windows cmd.exe never
 * has to resolve a local node_modules/.bin path.
 */

import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const node = process.execPath;

const children = [
  spawn(node, [path.join(root, 'scripts', 'dev-backend.mjs')], {
    cwd: root,
    env: process.env,
    stdio: 'inherit',
  }),
  spawn(node, [path.join(root, 'scripts', 'dev-frontend.mjs')], {
    cwd: root,
    env: process.env,
    stdio: 'inherit',
  }),
];

let shuttingDown = false;

function shutdown(signal) {
  if (shuttingDown) {
    return;
  }
  shuttingDown = true;
  for (const child of children) {
    if (!child.killed && child.exitCode === null) {
      child.kill(signal);
    }
  }
}

process.on('SIGINT', () => {
  shutdown('SIGINT');
});

process.on('SIGTERM', () => {
  shutdown('SIGTERM');
});

for (const child of children) {
  child.on('exit', (code, signal) => {
    if (shuttingDown) {
      return;
    }
    if (signal === 'SIGINT' || signal === 'SIGTERM') {
      shutdown('SIGTERM');
      process.exit(130);
      return;
    }
    if (code && code !== 0) {
      shutdown('SIGTERM');
      process.exit(code);
    }
  });
}

process.on('exit', () => shutdown('SIGTERM'));
