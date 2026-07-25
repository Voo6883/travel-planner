/**
 * Cross-platform dispatcher for `npm run wait`.
 *
 * Same reason as scripts/prereq.mjs: `bash` on Windows usually resolves to WSL, which cannot
 * see the host's Docker port bindings the way this check needs. On Windows the poll is done
 * here in Node; elsewhere it delegates to scripts/wait-for-services.sh, which stays the
 * documented entry point for Unix and CI (PLAN §4.0.0, backlog S0-3).
 */

import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptsDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(scriptsDir, '..');

if (process.platform !== 'win32') {
  const result = spawnSync('bash', [join(scriptsDir, 'wait-for-services.sh')], { stdio: 'inherit' });
  process.exit(result.status ?? 1);
}

/** Minimal .env reader — only for host/port, never for secrets. */
function readEnvFile() {
  const path = join(repoRoot, '.env');
  if (!existsSync(path)) return {};
  const out = {};
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const match = /^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/.exec(line);
    if (match) out[match[1]] = match[2].replace(/^["']|["']$/g, '');
  }
  return out;
}

const env = { ...readEnvFile(), ...process.env };
const port = env.BACKEND_HOST_PORT ?? '8080';
const host = env.BACKEND_HOST ?? 'localhost';
const timeoutSeconds = Number(env.TIMEOUT_SECONDS ?? 120);
const intervalMs = Number(env.POLL_INTERVAL_SECONDS ?? 3) * 1000;

// Readiness, not health: readiness includes the database, which is what "usable" means.
const readyUrl = `http://${host}:${port}/api/v1/ready`;
console.log(`Waiting for backend readiness at ${readyUrl} (timeout ${timeoutSeconds}s)`);

const deadline = Date.now() + timeoutSeconds * 1000;
while (Date.now() < deadline) {
  try {
    const response = await fetch(readyUrl, { signal: AbortSignal.timeout(5000) });
    if (response.ok) {
      console.log(`\nBackend ready: ${await response.text()}`);
      process.exit(0);
    }
  } catch {
    // Not up yet.
  }
  process.stdout.write('.');
  await new Promise((resolve) => setTimeout(resolve, intervalMs));
}

console.error(`\nTimed out after ${timeoutSeconds}s waiting for ${readyUrl}`);
console.error('Inspect with: npm run docker:ps && npm run docker:logs');
process.exit(1);
