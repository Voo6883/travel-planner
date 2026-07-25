#!/usr/bin/env bash
#
# Unit tests for scripts/check-prerequisites.sh
#
# Covers the two things that actually break in a prerequisite gate:
#   1. version parsing across every real-world format the tools emit
#   2. missing-command handling (must fail loudly, never pass silently)
#
# Run directly, or via `npm run prereq:test`.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${SCRIPT_DIR}/../check-prerequisites.sh"

# Load the helpers without running the check.
PREREQ_SOURCE_ONLY=1 . "$TARGET"

TESTS_RUN=0
TESTS_FAILED=0

assert_eq() {
  # assert_eq <description> <expected> <actual>
  TESTS_RUN=$((TESTS_RUN + 1))
  if [ "$2" = "$3" ]; then
    printf '  ok    %s\n' "$1"
  else
    TESTS_FAILED=$((TESTS_FAILED + 1))
    printf '  FAIL  %s\n        expected: [%s]\n        actual:   [%s]\n' "$1" "$2" "$3"
  fi
}

printf '\ncheck-prerequisites.sh - version parsing\n\n'

# --- node / npm ------------------------------------------------------------- #
assert_eq 'node -v            "v22.23.1"'                  '22' "$(version_major 'v22.23.1')"
assert_eq 'node -v            "v18.19.1"'                  '18' "$(version_major 'v18.19.1')"
assert_eq 'npm -v             "10.2.4"'                    '10' "$(version_major '10.2.4')"
assert_eq 'npm -v             "9.8.1"'                     '9'  "$(version_major '9.8.1')"

# --- java: legacy 1.x scheme vs modern -------------------------------------- #
assert_eq 'java modern        "21.0.11"'                   '21' "$(java_major 'openjdk version "21.0.11" 2026-04-21 LTS')"
assert_eq 'java legacy        "1.8.0_51" is Java 8'        '8'  "$(java_major 'java version "1.8.0_51"')"
assert_eq 'java legacy        "1.7.0_80" is Java 7'        '7'  "$(java_major 'java version "1.7.0_80"')"
assert_eq 'java modern        "17.0.19"'                   '17' "$(java_major 'openjdk version "17.0.19" 2025-10-21')"
assert_eq 'java two-digit     "25.0.3"'                    '25' "$(java_major 'openjdk version "25.0.3" 2026-01-20')"

# JAVA_TOOL_OPTIONS noise precedes the real version line on many CI images.
NOISY_JAVA='Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8
openjdk version "21.0.11" 2026-04-21 LTS
OpenJDK Runtime Environment Temurin-21.0.11+10 (build 21.0.11+10-LTS)'
assert_eq 'java with noise    picks the version line'      '21' "$(java_major "$(java_version_line "$NOISY_JAVA")")"

# --- docker / compose / git / gradle ---------------------------------------- #
assert_eq 'docker            "Docker version 28.5.2"'      '28' "$(version_major 'Docker version 28.5.2, build ecc6942')"
assert_eq 'docker old        "Docker version 20.10.7"'     '20' "$(version_major 'Docker version 20.10.7, build f0df350')"
assert_eq 'compose v2        "version v2.40.3-desktop.1"'  '2'  "$(version_major 'Docker Compose version v2.40.3-desktop.1')"
assert_eq 'compose v1        "docker-compose version 1.29.2"' '1' "$(version_major 'docker-compose version 1.29.2, build 5becea4c')"
assert_eq 'git windows       "2.40.1.windows.1"'           '2'  "$(version_major 'git version 2.40.1.windows.1')"
assert_eq 'git linux         "2.43.0"'                     '2'  "$(version_major 'git version 2.43.0')"
assert_eq 'gradle            "Gradle 8.7"'                 '8'  "$(version_major 'Gradle 8.7')"
assert_eq 'gradle two-digit  "Gradle 10.0"'                '10' "$(version_major 'Gradle 10.0')"

# --- degenerate input -------------------------------------------------------- #
assert_eq 'empty string      yields nothing'               ''   "$(version_major '')"
assert_eq 'no digits         yields nothing'               ''   "$(version_major 'command not found')"
assert_eq 'empty java        yields nothing'               ''   "$(java_major '')"
assert_eq 'no version line   yields nothing'               ''   "$(java_version_line 'bash: java: command not found')"
assert_eq 'extract full      keeps all components'         '2.40.1' "$(extract_version 'git version 2.40.1.windows.1')"

# --- missing commands -------------------------------------------------------- #
printf '\ncheck-prerequisites.sh - missing commands\n\n'

# An empty PATH makes every tool unresolvable. The gate must fail, not pass.
# "$BASH" is the absolute path of the running interpreter — a bare `bash` would be
# looked up under the emptied PATH and die with 127 before the script even starts.
EMPTY_PATH_OUTPUT="$(PATH='' "$BASH" "$TARGET" 2>&1)"
EMPTY_PATH_EXIT=$?
assert_eq 'empty PATH        exits non-zero'               '1'  "$EMPTY_PATH_EXIT"

if printf '%s' "$EMPTY_PATH_OUTPUT" | grep -q 'not found'; then
  FOUND_NOT_FOUND='yes'
else
  FOUND_NOT_FOUND='no'
fi
assert_eq 'empty PATH        reports "not found"'          'yes' "$FOUND_NOT_FOUND"

if printf '%s' "$EMPTY_PATH_OUTPUT" | grep -q 'FAILED'; then
  FOUND_FAILED='yes'
else
  FOUND_FAILED='no'
fi
assert_eq 'empty PATH        prints FAILED summary'        'yes' "$FOUND_FAILED"

# Install hints must accompany failures, otherwise the message is not actionable.
if printf '%s' "$EMPTY_PATH_OUTPUT" | grep -q 'adoptium.net'; then
  FOUND_HINT='yes'
else
  FOUND_HINT='no'
fi
assert_eq 'empty PATH        prints an install hint'       'yes' "$FOUND_HINT"

# --- summary ----------------------------------------------------------------- #
printf '\n%d assertions, %d failed\n\n' "$TESTS_RUN" "$TESTS_FAILED"
[ "$TESTS_FAILED" -eq 0 ] || exit 1
exit 0
