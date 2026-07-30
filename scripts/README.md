# `scripts/` — root tooling

Delivered by [Task 01](../tasks/01-prerequisite-root-tooling.md) (backlog `S0-1`).
Contract defined by [`plans/superpower/PLAN.md`](../plans/superpower/PLAN.md) §4.0.0 (LOCKED).

**Orchestration only.** No application logic lives here.

| File | Purpose |
|---|---|
| `check-prerequisites.sh` | Prerequisite gate — Unix / macOS / Git Bash |
| `check-prerequisites.ps1` | Prerequisite gate — Windows PowerShell |
| `prereq.mjs` | Cross-platform dispatcher behind `npm run prereq` |
| `dev-backend.mjs` | Spring Boot hot reload (`npm run dev:backend`) |
| `dev-frontend.mjs` | Next.js HMR dev server (`npm run dev:frontend`) |
| `dev-apps.mjs` | Runs backend + frontend together (`npm run dev:apps`) |
| `install-postgres-windows.ps1` | Native PostgreSQL 16 install to `D:\PostgreSQL\16` |
| `install-pgvector-windows.ps1` | pgvector extension for native Windows Postgres |
| `lib/repo.mjs` | Shared read-only helpers — repo paths, markdown sections, STATUS ledger parsing |
| `lib/code-map.mjs` | Derives the code map from the tree (ports, error codes, migrations, features, tasks) |
| `code-map.mjs` | Writes `docs/generated/CODE-MAP.json`; `--check` fails when it is stale (`npm run code-map`) |
| `task-context.mjs` | The per-task execution pack (`npm run task:context -- 21`) |
| `verify.mjs` | The three-stage gate ladder (`npm run verify:fast` / `verify:task` / `verify:full`) |
| `task-report.mjs` | Runs the real gates and writes `artifacts/task-NN-evidence.md` (`npm run task:report -- 18`) |
| `stale-docs.mjs` | Fails when agent-facing docs contradict the tree (`npm run docs:stale`) |
| `tests/test-check-prerequisites.sh` | Unit tests for the bash gate |
| `tests/check-prerequisites.tests.ps1` | Unit tests for the PowerShell gate |
| `tests/run-prereq-tests.mjs` | Cross-platform dispatcher behind `npm run prereq:test` |

---

## Running the gate

```bash
npm run prereq                      # any platform — dispatches to the right script
./scripts/check-prerequisites.sh    # Unix / macOS / Git Bash
```

```powershell
.\scripts\check-prerequisites.ps1   # Windows PowerShell
```

**Exit codes:** `0` = all required tools present and correct · `1` = at least one failure.

## Hot reload development (hybrid mode)

Start Postgres in Docker, then run both apps on the host with instant frontend HMR and backend
DevTools restarts:

```bash
cp .env.example .env   # once — set BACKEND_INTERNAL_URL=http://localhost:8080 for host dev
npm run dev:full       # Postgres + backend + frontend
```

| Command | What it does |
|---|---|
| `npm run dev` | Backend + frontend hot reload (no database) |
| `npm run dev:full` | `dev:db` then `dev` — recommended for full-stack work |
| `npm run dev:frontend` | Next.js only → http://localhost:3000 |
| `npm run dev:backend` | Spring Boot only → http://localhost:8080/api/v1 |
| `npm run dev:db` | Postgres/pgvector container only |

Scripts are cross-platform (Windows PowerShell/cmd and Unix). They load the root `.env` when
present and apply host defaults (`BACKEND_INTERNAL_URL=http://localhost:8080`,
`SPRING_DATASOURCE_URL=…localhost…`) so a Docker-oriented `.env.example` copy still works.

## Checks performed

| Tool | Requirement | Rule | Probe |
|---|---|---|---|
| Node.js | 22.x | exact major | `node -v` |
| npm | ≥10 | minimum major | `npm -v` |
| Java JDK | 21 | exact major | `java -version` |
| Docker | ≥24 | minimum major | `docker --version` |
| Docker Compose | ≥v2 | minimum major | `docker compose version` |
| Git | ≥2 | minimum major | `git --version` |
| Gradle wrapper | 8.x | exact major, **skipped when absent** | `apps/backend/gradlew --version` |

Every failure prints the detected version and an install hint. Thresholds are declared once
at the top of each script — change them in both, or the platforms drift.

### Non-fatal warning: `JAVA_HOME`

Gradle resolves the JDK through `JAVA_HOME`, not `PATH`. A machine can show Java 21 from
`java -version` while Gradle silently compiles against a different JDK. When they disagree the
gate emits `[WARN]` and continues — it does not affect the exit code, because the plan's required
tool list is what gates the build.

### Gradle wrapper is `[SKIP]`, not `[FAIL]`

`apps/backend/gradlew` does not exist until [Task 02](../tasks/02-backend-minimal-scaffold.md)
scaffolds the backend. Task 01 must pass on a repository with no application code, so absence is a
skip. Once the wrapper lands the check becomes active automatically — no script change needed.

---

## Why `npm run prereq` goes through Node

npm executes scripts via `cmd.exe` on Windows, which cannot run `check-prerequisites.sh`.
Routing through `bash` is not a fix either: on a typical Windows machine `bash.exe` resolves to
**WSL**, so the gate would report the *Linux* toolchain and return a confidently wrong `PASS`
while the Windows toolchain that actually builds the project goes unchecked. Verified on the
development machine:

```
> (Get-Command bash).Source
C:\windows\system32\bash.exe          # WSL, not Git Bash
```

`prereq.mjs` therefore dispatches on `process.platform` — `.ps1` on Windows, `.sh` everywhere
else. Both scripts stay directly runnable exactly as §4.0.0 documents.

## Why the bash script uses no external commands

`check-prerequisites.sh` parses versions with bash builtins (`[[ =~ ]]`, parameter expansion)
rather than `grep`/`cut`/`head`/`dirname`/`uname`. A prerequisite checker has to produce a
readable report on a machine whose `PATH` is broken — precisely when those utilities are
unreachable. The first version shelled out and died with exit 127 under `PATH=''` instead of
reporting the missing tools. `check-prerequisites.ps1` has the same property via .NET regex.

---

## Tests

```bash
npm run prereq:test
```

Runs the PowerShell suite on Windows and the bash suite wherever bash is available (on Windows,
Git Bash is located by absolute path — `bash` on `PATH` is usually WSL). No Pester or other test
framework is required. Both suites are assertion-for-assertion mirrors, so a parsing change on one
platform that is not mirrored on the other shows up as a failure.

### Test matrix

**Version parsing** — every format the real tools emit:

| Case | Input | Expected major |
|---|---|---|
| Node | `v22.23.1` | 22 |
| Node, wrong version | `v18.19.1` | 18 |
| npm | `10.2.4` | 10 |
| npm, too old | `9.8.1` | 9 |
| Java modern | `openjdk version "21.0.11" 2026-04-21 LTS` | 21 |
| Java legacy `1.x` | `java version "1.8.0_51"` | **8** |
| Java legacy `1.x` | `java version "1.7.0_80"` | **7** |
| Java modern | `openjdk version "17.0.19" 2025-10-21` | 17 |
| Java two-digit | `openjdk version "25.0.3" 2026-01-20` | 25 |
| Java behind noise | `Picked up JAVA_TOOL_OPTIONS…` + version line | 21 |
| Docker | `Docker version 28.5.2, build ecc6942` | 28 |
| Docker, too old | `Docker version 20.10.7, build f0df350` | 20 |
| Compose v2 | `Docker Compose version v2.40.3-desktop.1` | 2 |
| Compose v1 | `docker-compose version 1.29.2, build 5becea4c` | 1 |
| Git on Windows | `git version 2.40.1.windows.1` | 2 |
| Git on Linux | `git version 2.43.0` | 2 |
| Gradle | `Gradle 8.7` | 8 |
| Gradle two-digit | `Gradle 10.0` | 10 |
| Empty input | `""` | none |
| No digits | `command not found` | none |
| No version line | `bash: java: command not found` | none |
| Full version retained | `git version 2.40.1.windows.1` | `2.40.1` |

The `1.8.0_51 → 8` and `2.40.1.windows.1 → 2` rows are the two that a naive
"split on the first dot" parser gets wrong.

**Missing commands** — the gate runs with `PATH` emptied so no tool resolves:

| Assertion | Expected |
|---|---|
| Exit code | non-zero |
| Output contains `not found` | yes |
| Output contains `FAILED` summary | yes |
| Output contains an install hint | yes |

The last row exists because a failure with no install hint is not actionable, which is the whole
point of the gate.

### Platform coverage actually executed

| Platform | Gate | Tests | Result |
|---|---|---|---|
| Windows PowerShell 5.1 | ✅ pass and fail paths | ✅ 27/27 | Verified |
| Git Bash (msys, bash 5.2.15) | ✅ pass and fail paths | ✅ 27/27 | Verified |
| Linux / macOS | — | — | **Not executed** — no host available. The bash script uses only POSIX-compatible bash builtins, but this is untested and should be confirmed by CI in [Task 05](../tasks/05-ci-repository-workflow.md). |

---

## Handoff — what Tasks 02–05 call

| Task | Call | Notes |
|---|---|---|
| [02 — backend scaffold](../tasks/02-backend-minimal-scaffold.md) | `npm run prereq` | Must exit 0 before scaffolding. Once `apps/backend/gradlew` exists the Gradle row flips from `[SKIP]` to an active 8.x check — keep the wrapper on Gradle 8. |
| [03 — frontend scaffold](../tasks/03-frontend-minimal-scaffold.md) | `npm run prereq` | Root `package.json` stays orchestration-only; frontend dependencies belong in `apps/frontend/package.json`. `.nvmrc` (22) is already at the repo root. |
| [04 — Docker runtime](../tasks/04-docker-runtime.md) | `npm run prereq` | Docker ≥24 and Compose ≥v2 are already gated here; do not re-implement those checks in `wait-for-services.sh`. |
| [05 — CI](../tasks/05-ci-repository-workflow.md) | `npm run prereq` and `npm run prereq:test` | Run both on Ubuntu **and** Windows runners — that closes the Linux/macOS gap above. `prereq` is the environment gate; `prereq:test` guards the parser against regressions. |

**Adding a tool to the gate:** update the threshold constants and the check list in *both*
scripts, add parsing cases to *both* test suites, and update the table in this file. There is no
shared implementation between the two scripts by design — the mirrored test suites are what keep
them honest.

---

## Agent-efficiency tooling

Added in response to the 2026-07-29 dev-branch review §6. All five are dependency-free Node: the root
`package.json` has no `dependencies` and no `devDependencies` (PLAN §4.0.0 — orchestration only), and
a gate that needs an install is a gate that gets skipped the week the install breaks.

```bash
npm run task:context -- 21     # ~4 KB: dependency gate, DoD, resolved PLAN line ranges, findings
npm run code-map               # regenerate docs/generated/CODE-MAP.json
npm run code-map:check         # fail if the committed map is stale (CI runs this)
npm run docs:stale             # fail if a doc contradicts the tree (CI runs this)

npm run verify:fast            # every edit — scoped by git diff. Seconds.
npm run verify:task -- 18      # task completion — coverage, ArchUnit, drift. A minute.
npm run verify:full            # what CI runs, Testcontainers included. Needs Docker.

npm run task:report -- 18      # runs the real gates, writes artifacts/task-18-evidence.md
```

### What each one is for

| Command | The waste it removes |
|---|---|
| `task:context` | Re-reading 7,500+ lines of planning docs to establish a dozen facts, once per task. |
| `code-map` | Repository-wide `grep` for "who implements this port", "what is the next migration", "is this error code translated". |
| `verify:*` | Paying Testcontainers and a production build to find out a one-line fix compiles. |
| `task:report` | Hand-assembled evidence — which can be paraphrased, or quoted from a run three commits old. |
| `docs:stale` | An agent acting confidently on documentation that stopped being true. |

### Deliberate limits

- **`code-map` is regexes, not an AST walk.** Its output decides which three files an agent opens.
  Being wrong about an unusual declaration costs one extra file read; a parser would cost a
  dependency this package is not allowed to have. Ambiguity is omitted rather than guessed.
- **`task:report` does not write `tasks/STATUS.md`.** "CI is green for this commit" is a fact only the
  CI provider can assert, and a local script that wrote `done` on the strength of a local run would be
  manufacturing the evidence the gate exists to demand. It prints the ledger line to paste, and the
  conditions under which pasting it is honest.
- **`stale-docs` only checks claims a machine can decide.** Each assertion pairs a claim pattern with a
  contradicting condition and fires only when both hold. "Is this paragraph still accurate" is not
  checkable, and a gate with false positives is a gate that gets switched off.
