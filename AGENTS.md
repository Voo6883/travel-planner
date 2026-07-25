# AGENTS.md

## Cursor Cloud specific instructions

### Repository status: planning phase (no application code yet)

This repo currently contains only documentation:

- `README.md` — product overview and planned quick start.
- `plans/superpower/PLAN.md` — the locked architecture/plan (this is the source of truth).

There is **no scaffolded application yet**: no `package.json`, no `apps/frontend`
(Next.js) or `apps/backend` (Spring Boot), no `docker-compose.yml`, no source, tests,
lint config, or build tooling. Consequently there is currently **nothing to install,
lint, test, build, or run**. Any "run the app" request cannot be fulfilled until the
Phase 0 scaffold described in `plans/superpower/PLAN.md` §10 exists.

### Preinstalled toolchain (already satisfies the plan)

The base VM already provides the runtimes the plan requires — do **not** reinstall them:

- Node.js 22.x (`node -v`) — matches frontend requirement.
- JDK 21 (`java -version`) — matches backend requirement.
- npm 10.x, git 2.x.

Not preinstalled (add only once the corresponding code lands):

- Docker + Compose — needed for the planned `docker compose up` full-stack runtime.
- Maven or Gradle — backend build tool is still TBD (`plans/superpower/PLAN.md` §11).

### When the app gets scaffolded

Once code exists, follow the commands documented in `README.md` ("Quick start") and
`plans/superpower/PLAN.md` §4.0.0 rather than duplicating them here — e.g. `npm run prereq`,
`cp .env.example .env`, `docker compose up --build` (full stack) or
`docker compose -f docker-compose.dev.yml up -d` (Postgres only, apps on host).
Frontend runs on port 3000, backend API on `:8080/api/v1`, Postgres on `:5432`.
Dev admin seed: username `ADMIN`, password `123456` (dev/docker only).
