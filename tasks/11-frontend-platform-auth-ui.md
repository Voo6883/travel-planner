# Task 11 — Frontend Platform and Authentication UI

## Objective

Complete the reusable frontend runtime and connect all account/session flows to the generated backend API.

## Dependencies

- Tasks 03, 06, 08, 09, and 10 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.2
- `docs/UI-UX-DESIGN-SYSTEM.md`
- `plans/BACKLOG.md` S2-6 through S2-12, excluding PWA/admin work owned by Tasks 12–13
- `plans/USE-CASES.md` authentication use cases

## Scope

### Shared frontend runtime

- Configure TanStack Query provider, query-key factory, standard API error mapping, request IDs, and cookie credentials.
- Validate API responses at the edge with Zod where generated types alone are insufficient.
- Implement final design tokens, Tailwind semantic utilities, Ant Design theme, PageShell, AppShell, and responsive navigation foundations.
- Complete `next-intl` English and Malay namespaces for shared/auth content.

### Authentication UI

- Login and register routes under `(auth)`.
- Local login/register forms using Ant Design Form and typed generated requests.
- Shared Gmail sign-in button on login and register.
- GitHub sign-in redirect flow and callback/result handling.
- Forgot/reset password, verify email, resend verification, profile/provider display, change password, logout, and delete-account UI.
- Route/session guards for authenticated planner pages.
- Clear pending, popup/redirect, provider conflict, verification, lockout, and account-deleted states.

### Testing

- Component tests for forms and provider states.
- Mock Service Worker or equivalent generated-contract mocks.
- E2E coverage using stub/local providers; live provider smoke tests remain optional/manual.
- Responsive and keyboard checks at 320 px and desktop widths.

## Do not

- Do not handwrite backend DTO copies.
- Do not import Firebase Admin or provider secrets into the frontend.
- Do not implement admin, trip, chat, or PWA features here.
- Do not fetch directly in `page.tsx` files.

## Validation

```bash
cd apps/frontend
npm run lint
npm run test
npm run build
```

Run authentication E2E against the Docker stack with stub/local provider configuration.

## Definition of Done

- All planned account flows have usable UI and typed API integration.
- Session guards and error states work across refreshes.
- English/Malay UI is complete for this scope.
- Frontend architecture is ready for feature modules.

## Handoff

Document auth route map, provider setup, query keys, shared components, and test fixtures.

## Suggested branch and commit

- Branch: `agent/task-11-frontend-auth-platform`
- Commit: `feat: build frontend platform and auth UI`
