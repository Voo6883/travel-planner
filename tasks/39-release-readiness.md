# Task 39 — Production Configuration and Release Readiness

## Objective

Prepare the implemented project for a secure, supportable deployment without claiming unimplemented or stub-only integrations as live.

## Dependencies

- Task 38 complete with no unresolved release-blocking failures.

## Required reading

- `plans/superpower/PLAN.md` production/security/observability/deployment sections
- All ADRs
- Task 38 verification report
- `README.md` and `.env.example`

## Scope

### Configuration and security

- Production profile with validated required environment variables and safe defaults.
- Secret-manager/deployment guidance; no secrets in source, image, frontend bundle, or logs.
- Secure cookies, CSRF/CORS, callback URLs, CSP/security headers, TLS/proxy assumptions, rate limits, and trusted origins.
- Disable dev ADMIN seed and stub/demo labeling in production.
- Explicit provider mode matrix showing stub, disabled, and live adapters.
- Database connection pool, Flyway startup/rollback policy, backup/restore, retention/anonymization, and migration runbook.

### Operations

- Health/readiness semantics, graceful shutdown/background-job handling, structured logging, metrics, request correlation, AI/provider/cost dashboards, alert suggestions, and reconciliation procedures.
- Deployment/runbook for frontend, backend, PostgreSQL/pgvector, optional Redis, domain/mail/OAuth callbacks, and PWA updates.
- Smoke test and rollback checklist.

### Documentation

- Update README status, actual quick start, architecture summary, implemented/stub/live capability table, required/optional env vars, test commands, known limitations, and troubleshooting.
- Resolve public/private/license wording through an explicit owner decision.
- Update `AGENTS.md`, backlog/task status, and compatibility review to reflect actual implementation.
- Add release notes/changelog and demo/sample data reset instructions where relevant.

### Final gate

- Run production-like build/deploy smoke test with approved non-production credentials/fixtures.
- Confirm no dev seed, secret, debug endpoint, permissive CORS, or stub presented as live.

## Do not

- Do not commit production credentials.
- Do not enable unselected live suppliers.
- Do not claim global/current data coverage beyond the implemented knowledge sources.
- Do not release with failed migrations or unresolved critical verification issues.

## Definition of Done

- Production configuration is explicit and secure.
- Deployment, rollback, backup, monitoring, and recovery are documented.
- Repository documentation matches reality.
- Owner can make a clear release/no-release decision from the evidence.

## Handoff

Publish final release checklist, deployed versions/config modes, runbooks, residual risks, and post-release monitoring plan.

## Suggested branch and commit

- Branch: `agent/task-39-release-readiness`
- Commit: `docs: prepare production release readiness`
