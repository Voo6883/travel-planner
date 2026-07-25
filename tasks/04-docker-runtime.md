# Task 04 — Docker Runtime and Service Orchestration

## Objective

Make the frontend, backend, and PostgreSQL/pgvector stack reproducibly runnable through Docker Compose.

## Dependencies

- Tasks 01, 02, and 03 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.0.0 and §4.0.0.2
- `plans/BACKLOG.md` S0-2 and S0-3
- `.env.example`

## Scope

- `docker/backend/Dockerfile` multi-stage build using Java 21 runtime.
- `docker/frontend/Dockerfile` multi-stage Node 22 build/runtime.
- Non-root runtime users where practical.
- `docker-compose.yml` with `postgres`, `backend`, and `frontend`.
- PostgreSQL 16 with pgvector image and persistent named volume.
- `docker-compose.dev.yml` for PostgreSQL-only hybrid development.
- Service health checks and `depends_on` health conditions.
- `scripts/wait-for-services.sh` polling backend readiness with a bounded timeout.
- Root scripts for `docker:up`, `docker:down`, and development modes.
- `.dockerignore` files preventing `.env`, secrets, build outputs, and VCS metadata from entering images.
- Correct distinction between Docker hostname `postgres` and host-run `localhost`.

## Do not

- Do not copy `.env` or provider credentials into images.
- Do not introduce Redis before Task 37.
- Do not seed business data or a dev admin yet.

## Validation

```bash
npm run prereq
docker compose up --build -d
./scripts/wait-for-services.sh
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/api/v1/ready
docker compose down
```

Also verify the PostgreSQL-only development compose file.

## Definition of Done

- Full stack becomes healthy from a clean checkout.
- Readiness confirms database reachability.
- Images contain no secrets and run with bounded health checks.
- Restart preserves PostgreSQL data.

## Handoff

Report service names, ports, profiles, volumes, and environment assumptions.

## Suggested branch and commit

- Branch: `agent/task-04-docker-runtime`
- Commit: `build: add Docker Compose runtime`
