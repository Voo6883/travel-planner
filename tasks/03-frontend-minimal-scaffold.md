# Task 03 — Frontend Minimal Scaffold

## Objective

Create the smallest valid Next.js 15 App Router frontend while preserving the planned feature-based structure.

## Dependencies

- Task 01 complete.
- May run in parallel with Task 02.

## Required reading

- `plans/superpower/PLAN.md` §4.2.1–§4.2.6
- `docs/UI-UX-DESIGN-SYSTEM.md`
- `plans/BACKLOG.md` S2-6, S2-7, S2-8, S2-12

## Scope

Under `apps/frontend/`:

- Next.js 15 App Router, TypeScript strict mode, Node 22 engine.
- Tailwind and Ant Design dependencies with a minimal, non-final theme bridge.
- `next-intl` foundation with English and Malay locale loading.
- Route groups/placeholders for `(auth)`, `(planner)`, and `(admin)` without feature logic.
- Initial `components/ui`, `components/layout`, `features`, `lib`, `styles`, `locales`, and `generated` boundaries.
- Minimal home/status screen that can render without the backend.
- A typed temporary health client or placeholder boundary to be replaced by generated API code in Task 06.
- Lint, type-check, test, and production build commands.
- Basic accessibility and responsive smoke checks.

## Do not

- Do not implement authentication, trip planning, admin features, PWA service worker, or handwritten permanent API DTOs.
- Do not add arbitrary visual tokens outside the design-token location.
- Do not fetch data directly from route pages.

## Validation

```bash
cd apps/frontend
npm ci
npm run lint
npm run test
npm run build
```

Verify English and Malay shells render and there is no horizontal overflow at 320 px.

## Definition of Done

- Strict TypeScript build succeeds.
- App Router pages stay thin.
- The project is ready for generated API types and React Query.
- UI structure follows the design-system authority.

## Handoff

Report the chosen test runner, route map, aliases, and temporary health-client replacement point.

## Suggested branch and commit

- Branch: `agent/task-03-frontend-scaffold`
- Commit: `build: scaffold Next.js frontend`
