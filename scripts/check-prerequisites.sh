#!/usr/bin/env bash
#
# Travel Planner — prerequisite check (Unix / macOS / Git Bash)
# Mirrors scripts/check-prerequisites.ps1. Keep both in sync: same tools, same
# thresholds, same output shape, same exit codes.
#
# Required versions are defined by plans/superpower/PLAN.md 4.0.0 (LOCKED).
# Exit codes: 0 = every required tool present and correct, 1 = at least one failure.
#
# Source with PREREQ_SOURCE_ONLY=1 to load the parsing helpers without running
# the check (used by scripts/tests/test-check-prerequisites.sh).

set -uo pipefail

# --------------------------------------------------------------------------- #
# Required versions (single source of truth for this script)
# --------------------------------------------------------------------------- #
REQ_NODE_MAJOR=22      # exact
REQ_NPM_MAJOR=10       # minimum
REQ_JAVA_MAJOR=21      # exact
REQ_DOCKER_MAJOR=24    # minimum
REQ_COMPOSE_MAJOR=2    # minimum
REQ_GIT_MAJOR=2        # minimum
REQ_GRADLE_MAJOR=8     # exact, only when the wrapper is present

# --------------------------------------------------------------------------- #
# Version parsing helpers — pure functions, covered by the test script
#
# These use bash builtins only — no grep/cut/head/dirname/uname. A prerequisite
# checker must still produce a readable report on a machine whose PATH is broken
# or empty, which is exactly when external utilities are unavailable. This also
# matches check-prerequisites.ps1, which parses with .NET regex and has no
# external dependencies either.
# --------------------------------------------------------------------------- #

# Extract the first dotted numeric version from arbitrary text.
# "Docker version 28.5.2, build ecc6942" -> "28.5.2"
# "git version 2.40.1.windows.1"         -> "2.40.1"
extract_version() {
  local text="${1:-}"
  if [[ "$text" =~ ([0-9]+(\.[0-9]+)*) ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  fi
}

# Major component of a dotted version. "22.23.1" -> "22"
version_major() {
  local v
  v="$(extract_version "${1:-}")"
  [ -n "$v" ] && printf '%s' "${v%%.*}"
}

# Java uses a legacy scheme below 9: "1.8.0_51" is Java 8, "21.0.11" is Java 21.
java_major() {
  local v rest
  v="$(extract_version "${1:-}")"
  [ -z "$v" ] && return 0
  case "$v" in
    1.*) rest="${v#1.}"; printf '%s' "${rest%%.*}" ;;
    *)   printf '%s' "${v%%.*}" ;;
  esac
}

# Pull the version out of `java -version` output, which goes to stderr and may be
# preceded by noise such as "Picked up JAVA_TOOL_OPTIONS".
java_version_line() {
  local line
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      version\ *|*[[:space:]]version\ *) printf '%s' "$line"; return 0 ;;
    esac
  done <<< "${1:-}"
}

# First line of a multi-line string, without spawning `head`.
first_line() {
  local line
  while IFS= read -r line || [ -n "$line" ]; do
    printf '%s' "$line"; return 0
  done <<< "${1:-}"
}

# --------------------------------------------------------------------------- #
# Reporting
# --------------------------------------------------------------------------- #
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
WARN_COUNT=0

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_OK=$'\033[32m'; C_FAIL=$'\033[31m'; C_WARN=$'\033[33m'; C_DIM=$'\033[90m'; C_OFF=$'\033[0m'
else
  C_OK=''; C_FAIL=''; C_WARN=''; C_DIM=''; C_OFF=''
fi

report() {
  # report <status> <tool> <detected> <requirement>
  local status="$1" tool="$2" detected="$3" requirement="$4" tag colour
  case "$status" in
    ok)   tag='[ OK ]'; colour="$C_OK";   PASS_COUNT=$((PASS_COUNT + 1)) ;;
    fail) tag='[FAIL]'; colour="$C_FAIL"; FAIL_COUNT=$((FAIL_COUNT + 1)) ;;
    warn) tag='[WARN]'; colour="$C_WARN"; WARN_COUNT=$((WARN_COUNT + 1)) ;;
    skip) tag='[SKIP]'; colour="$C_DIM";  SKIP_COUNT=$((SKIP_COUNT + 1)) ;;
  esac
  printf '  %s%s%s  %-18s %-24s %s%s%s\n' \
    "$colour" "$tag" "$C_OFF" "$tool" "$detected" "$C_DIM" "$requirement" "$C_OFF"
}

hint() {
  printf '          %s-> %s%s\n' "$C_DIM" "$1" "$C_OFF"
}

# --------------------------------------------------------------------------- #
# Individual checks
# --------------------------------------------------------------------------- #

# check_tool <label> <command> <version-args> <mode:exact|min> <required-major> <hint>
check_tool() {
  local label="$1" cmd="$2" args="$3" mode="$4" required="$5" install_hint="$6"
  local raw detected major

  if ! command -v "$cmd" >/dev/null 2>&1; then
    report fail "$label" 'not found' "required ${mode/exact/}${required}"
    hint "$install_hint"
    return
  fi

  raw="$("$cmd" $args 2>&1)"
  detected="$(extract_version "$raw")"
  major="$(version_major "$raw")"

  if [ -z "$major" ]; then
    report fail "$label" 'unparseable' "required ${required}"
    hint "Could not read a version from: $(first_line "$raw")"
    return
  fi

  if [ "$mode" = 'exact' ] && [ "$major" != "$required" ]; then
    report fail "$label" "$detected" "required ${required}.x"
    hint "$install_hint"
  elif [ "$mode" = 'min' ] && [ "$major" -lt "$required" ]; then
    report fail "$label" "$detected" "required >=${required}"
    hint "$install_hint"
  else
    [ "$mode" = 'exact' ] && report ok "$label" "$detected" "required ${required}.x" \
                          || report ok "$label" "$detected" "required >=${required}"
  fi
}

check_java() {
  local raw line detected major
  if ! command -v java >/dev/null 2>&1; then
    report fail 'Java JDK' 'not found' "required ${REQ_JAVA_MAJOR}"
    hint "Install Adoptium Temurin ${REQ_JAVA_MAJOR}: https://adoptium.net/"
    return
  fi
  raw="$(java -version 2>&1)"
  line="$(java_version_line "$raw")"
  detected="$(extract_version "$line")"
  major="$(java_major "$line")"

  if [ -z "$major" ]; then
    report fail 'Java JDK' 'unparseable' "required ${REQ_JAVA_MAJOR}"
    hint "Could not read a version from: $(first_line "$raw")"
  elif [ "$major" != "$REQ_JAVA_MAJOR" ]; then
    report fail 'Java JDK' "$detected (Java $major)" "required ${REQ_JAVA_MAJOR}"
    hint "Install Adoptium Temurin ${REQ_JAVA_MAJOR}: https://adoptium.net/"
  else
    report ok 'Java JDK' "$detected" "required ${REQ_JAVA_MAJOR}"
  fi

  # Gradle resolves the JDK through JAVA_HOME, not PATH. A mismatch builds with the
  # wrong compiler while `java -version` looks correct, so surface it — non-fatal.
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    local home_major
    home_major="$(java_major "$(java_version_line "$("${JAVA_HOME}/bin/java" -version 2>&1)")")"
    if [ -n "$home_major" ] && [ "$home_major" != "$REQ_JAVA_MAJOR" ]; then
      report warn 'JAVA_HOME' "Java $home_major" "expected ${REQ_JAVA_MAJOR}"
      hint "JAVA_HOME=${JAVA_HOME} resolves to Java ${home_major}; Gradle will use it."
    fi
  fi
}

check_compose() {
  local raw detected major
  if ! command -v docker >/dev/null 2>&1; then
    report fail 'Docker Compose' 'not found' "required >=v${REQ_COMPOSE_MAJOR}"
    hint 'Included with Docker Desktop: https://www.docker.com/products/docker-desktop/'
    return
  fi
  raw="$(docker compose version 2>&1)"
  major="$(version_major "$raw")"
  detected="$(extract_version "$raw")"

  if [ -z "$major" ]; then
    if command -v docker-compose >/dev/null 2>&1; then
      report fail 'Docker Compose' 'v1 (docker-compose)' "required >=v${REQ_COMPOSE_MAJOR}"
      hint 'Compose v1 is end-of-life. Upgrade to Docker Desktop with the v2 plugin.'
    else
      report fail 'Docker Compose' 'not found' "required >=v${REQ_COMPOSE_MAJOR}"
      hint 'Included with Docker Desktop: https://www.docker.com/products/docker-desktop/'
    fi
  elif [ "$major" -lt "$REQ_COMPOSE_MAJOR" ]; then
    report fail 'Docker Compose' "$detected" "required >=v${REQ_COMPOSE_MAJOR}"
    hint 'Upgrade Docker Desktop to get the Compose v2 plugin.'
  else
    report ok 'Docker Compose' "$detected" "required >=v${REQ_COMPOSE_MAJOR}"
  fi
}

# The wrapper does not exist until Task 02 scaffolds the backend, so absence is a
# skip rather than a failure.
check_gradle_wrapper() {
  local wrapper="${REPO_ROOT}/apps/backend/gradlew" raw detected major
  if [ ! -f "$wrapper" ]; then
    report skip 'Gradle wrapper' 'not present' 'checked once apps/backend/gradlew exists'
    return
  fi
  if [ ! -x "$wrapper" ]; then
    report fail 'Gradle wrapper' 'not executable' "required ${REQ_GRADLE_MAJOR}.x"
    hint "chmod +x apps/backend/gradlew"
    return
  fi
  local all line
  all="$("$wrapper" --version 2>&1)"
  raw=''
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in Gradle\ *) raw="$line"; break ;; esac
  done <<< "$all"
  detected="$(extract_version "$raw")"
  major="$(version_major "$raw")"

  if [ -z "$major" ]; then
    report fail 'Gradle wrapper' 'unparseable' "required ${REQ_GRADLE_MAJOR}.x"
    hint 'Run: apps/backend/gradlew --version'
  elif [ "$major" != "$REQ_GRADLE_MAJOR" ]; then
    report fail 'Gradle wrapper' "$detected" "required ${REQ_GRADLE_MAJOR}.x"
    hint "Update the wrapper: apps/backend/gradlew wrapper --gradle-version ${REQ_GRADLE_MAJOR}"
  else
    report ok 'Gradle wrapper' "$detected" "required ${REQ_GRADLE_MAJOR}.x"
  fi
}

# --------------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------------- #
main() {
  # Resolved with parameter expansion rather than `dirname` so the script still
  # works when PATH is empty — see the parsing-helpers note above.
  local script_dir="${BASH_SOURCE[0]%/*}"
  [ "$script_dir" = "${BASH_SOURCE[0]}" ] && script_dir='.'
  REPO_ROOT="$(cd "${script_dir}/.." && pwd)"

  printf '\nTravel Planner - prerequisite check\n'
  printf '%sRepo:     %s%s\n' "$C_DIM" "$REPO_ROOT" "$C_OFF"
  printf '%sPlatform: %s (bash %s)%s\n\n' "$C_DIM" "${OSTYPE:-unknown}" "${BASH_VERSION%%(*}" "$C_OFF"

  check_tool 'Node.js' node '-v' exact "$REQ_NODE_MAJOR" \
    "Install Node ${REQ_NODE_MAJOR}: nvm install ${REQ_NODE_MAJOR} (or https://nodejs.org/)"
  check_tool 'npm' npm '-v' min "$REQ_NPM_MAJOR" \
    "npm ships with Node ${REQ_NODE_MAJOR}; updating Node updates npm"
  check_java
  check_tool 'Docker' docker '--version' min "$REQ_DOCKER_MAJOR" \
    'Install Docker Desktop: https://www.docker.com/products/docker-desktop/'
  check_compose
  check_tool 'Git' git '--version' min "$REQ_GIT_MAJOR" \
    'Install Git: https://git-scm.com/'
  check_gradle_wrapper

  printf '\n%d passed, %d failed, %d skipped' "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT"
  [ "$WARN_COUNT" -gt 0 ] && printf ', %d warning(s)' "$WARN_COUNT"
  printf '\n'

  if [ "$FAIL_COUNT" -gt 0 ]; then
    printf '%sFAILED%s - install the tools listed above, then re-run: npm run prereq\n\n' \
      "$C_FAIL" "$C_OFF"
    return 1
  fi
  printf '%sOK%s - prerequisites satisfied.\n\n' "$C_OK" "$C_OFF"
  return 0
}

if [ "${PREREQ_SOURCE_ONLY:-0}" != '1' ]; then
  main "$@"
  exit $?
fi
