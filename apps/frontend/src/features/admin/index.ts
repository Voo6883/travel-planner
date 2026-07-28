/**
 * Account administration (UC-A15, UC-A16, PLAN §4.0.6).
 *
 * The public surface of the feature: routes under `app/(admin)/` compose these and nothing else.
 * The `(admin)` shell, the `ADMIN` role guard, and the navigation entry belong to task 11 and are
 * deliberately not re-exported here — there is one admin shell, and this feature lives inside it.
 */
export { UserListPanel } from './components/user-list-panel';
export { UserDetailPanel } from './components/user-detail-panel';
export { AccountStatusTag } from './components/account-status-tag';
export {
  useAdminUser,
  useAdminUsers,
  useResetAdminUserPassword,
  useSetAdminUserEnabled,
} from './hooks/use-admin-users';
export { adminUserRoute } from './lib/admin-format';
