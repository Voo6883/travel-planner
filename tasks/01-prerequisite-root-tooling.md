# Task 01 — Prerequisite and Root Tooling

## Objective

Implement the cross-platform prerequisite gate and root orchestration required by Sprint 0 / S0-1.

## Dependencies

- Task 00 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0.0 and §4.0.0.1–2
- `plans/BACKLOG.md` S0-1
- `AGENTS.md`

## Scope

Create the root-level tooling only:

- Root `package.json` containing orchestration scripts, not application business logic.
- `.nvmrc` for Node.js 22.
- `scripts/check-prerequisites.sh` for Unix/macOS/Git Bash.
- `scripts/check-prerequisites.ps1` for Windows PowerShell.
- Checks for Node 22, npm 10+, Java 21, Docker, Docker Compose v2, Git, and the Gradle wrapper when present.
- Clear install hints, detected versions, and non-zero failure exit codes.
- Consistent output and behavior across both scripts.
- Unit-style script tests or a documented test matrix for version parsing and missing commands.

## Do not

- Do not scaffold Next.js or Spring Boot.
- Do not install tools automatically.
- Do not add credentials or a real `.env`.
- Do not place app dependencies in the root package.

## Validation

Run on supported environments or CI fixtures:

```bash
npm run prereq
./scripts/check-prerequisites.sh
```

```powershell
./scripts/check-prerequisites.ps1
```

Verify failure messages for at least one missing or wrong-version tool.

## Definition of Done

- Correct environments exit 0.
- Missing/wrong versions exit non-zero with actionable hints.
- Root package remains orchestration-only.
- Windows and Unix behavior is documented and tested.

## Handoff

Provide the exact scripts Tasks 02–05 should call.

## Suggested branch and commit

- Branch: `agent/task-01-root-tooling`
- Commit: `build: add prerequisite and root tooling`
