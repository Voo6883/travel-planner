#!/usr/bin/env bash
#
# Block until the backend reports ready, or fail with a non-zero exit (PLAN §4.0.0, backlog S0-3).
#
# Polls /api/v1/ready — not /health — because readiness includes database reachability, which is
# what "the stack is usable" actually means.
#
#   ./scripts/wait-for-services.sh              # defaults below
#   TIMEOUT_SECONDS=180 ./scripts/wait-for-services.sh
#   BACKEND_HOST_PORT=8081 ./scripts/wait-for-services.sh

set -uo pipefail

TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-3}"

# Load the root .env if present so the host port matches what Compose actually bound.
SCRIPT_DIR="${BASH_SOURCE[0]%/*}"
[ "$SCRIPT_DIR" = "${BASH_SOURCE[0]}" ] && SCRIPT_DIR='.'
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ -f "${REPO_ROOT}/.env" ]; then
  # shellcheck disable=SC1091
  set -a; . "${REPO_ROOT}/.env"; set +a
fi

HOST="${BACKEND_HOST:-localhost}"
PORT="${BACKEND_HOST_PORT:-8080}"
READY_URL="http://${HOST}:${PORT}/api/v1/ready"

if command -v curl >/dev/null 2>&1; then
  probe() { curl -fsS --max-time 5 "$1" 2>/dev/null; }
elif command -v wget >/dev/null 2>&1; then
  probe() { wget -qO- --timeout=5 "$1" 2>/dev/null; }
else
  printf 'wait-for-services: neither curl nor wget is available\n' >&2
  exit 1
fi

printf 'Waiting for backend readiness at %s (timeout %ss)\n' "$READY_URL" "$TIMEOUT_SECONDS"

elapsed=0
while [ "$elapsed" -lt "$TIMEOUT_SECONDS" ]; do
  if body="$(probe "$READY_URL")"; then
    printf '\nBackend ready after %ss: %s\n' "$elapsed" "$body"
    exit 0
  fi
  printf '.'
  sleep "$POLL_INTERVAL_SECONDS"
  elapsed=$((elapsed + POLL_INTERVAL_SECONDS))
done

printf '\nTimed out after %ss waiting for %s\n' "$TIMEOUT_SECONDS" "$READY_URL" >&2
printf 'Inspect with: docker compose ps && docker compose logs backend\n' >&2
exit 1
