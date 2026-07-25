<!--
  Required on every PR (PLAN §12.3, backlog S0-5).
  Workflow reference: docs/AI-AGENT-WORKFLOW.md · Scope control: docs/AGENT-HARNESS.md
  Delete nothing. Mark items N/A with a one-line reason rather than removing them.
-->

## Task

| Field | Value |
|---|---|
| Task ID | `tasks/NN-*.md` |
| Feature (C1–C5) | |
| Acceptance criteria (`UC-*`) | |
| Source commit | |
| Dependencies met | per `tasks/STATUS.md` ledger |

## Scope contract

<!-- docs/AGENT-HARNESS.md §5 — complete this BEFORE the first edit. -->

- **Files touched:**
- **Explicitly NOT doing** (from the brief's `Do not`):
- **Conflicts / unknowns found:**  → blocked? yes / no

> If conflicts are unresolved, the correct action is to ask — not to merge.

## Evidence

<!--
  docs/AGENT-HARNESS.md §6. Paste REAL command output. "Tests should pass" is not evidence.
  Quote the brief's Definition of Done item by item and show evidence for each.
-->

```
# paste actual output
```

- [ ] Definition of Done quoted item by item, each with evidence
- [ ] Commands actually run, real output pasted
- [ ] Anything skipped or failed is stated plainly

## Implementation report

<!-- tasks/README.md §"Standard task completion report" -->

1. **Files and layers changed:**
2. **Architecture decisions or deviations:**
3. **Tests and commands run, with results:**
4. **Stubbed vs live integrations:**
5. **Known limitations:**
6. **Database migration and compatibility notes:**
7. **Handoff state for dependent tasks:**

## Pre-merge checklist (PLAN §12.3)

**Always**

- [ ] `npm run prereq` passes; Docker stack starts if infra touched
- [ ] Change maps to a locked feature or documented sub-task — no architecture drift
- [ ] Changes land in the correct app; root scripts updated if needed
- [ ] Frontend on Node 22; backend compiled with Java 21
- [ ] **No secrets** — env/config only; nothing committed
- [ ] Line length ≤ 120; function params ≤ 3
- [ ] Naming: camelCase functions · kebab-case files/URLs · snake_case i18n
- [ ] Minimal diff — no drive-by refactors or unrelated edits

**Backend (if touched)**

- [ ] Controllers thin — routing/mapping only; logic in `application/`
- [ ] Domain has no framework imports; DTO ≠ domain ≠ JPA entity
- [ ] Service rules covered by unit tests with mocked ports
- [ ] `@Transactional(rollbackFor = Exception.class)` on write use-cases
- [ ] **No LLM or external HTTP inside a transaction**
- [ ] `@Version` / lock ordering where concurrent (ADR 008)
- [ ] Data access filtered by `user_id`; no tenant fields
- [ ] ArchUnit layer tests pass *(active from task 15)*

**Frontend (if touched)**

- [ ] Page thin; logic in `features/`; hook → `lib/api/` → generated types
- [ ] No cross-feature imports; public API via `features/*/index.ts`
- [ ] Forms use `onValuesChange`
- [ ] Tailwind from design tokens; no CSS Modules; no raw hex outside `design-tokens.ts`
- [ ] No hardcoded user-facing strings — snake_case keys, `en` **and** `ms`
- [ ] PWA rules respected; no auth API caching *(from task 13)*

**Contract (if touched)**

- [ ] `/api/v1/` paths; GET/POST/PUT/DELETE only — **no PATCH**
- [ ] Error envelope `{ code, message, details }`; code registered in the catalog
- [ ] OpenAPI updated and TS client regenerated — no hand-written API types

**AI (if touched)**

- [ ] LangChain4j types stay inside `ai/langchain4j/`
- [ ] LLM output schema-validated before persist or side effect
- [ ] Golden-file / schema tests for structured output
- [ ] **No LLM confirms a booking**
- [ ] Knowledge claims carry `source_refs[]`; no stub Knowledge adapter in prod (ADR 010)

## UI changes

<!-- Screenshots required if UI changed: desktop + 320 px, light + dark, en + ms. -->

## Branch and review policy

- Branch naming: `agent/task-NN-short-desc` · one task per PR
- Started from the latest accepted trunk
- Tasks execute in dependency order per `tasks/STATUS.md`

> **Current deviation:** implementation is landing directly on `dev` as feature commits rather
> than one branch/PR per task. Tracked as **F-8** in `tasks/EXECUTION-BASELINE.md`.
