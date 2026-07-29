import type { components } from '@/generated/api/schema';
import { apiRequest } from './client';
import { adminUserDetailSchema, adminUserPageSchema } from './schemas/admin.schema';

/**
 * Account administration (UC-A15, UC-A16, PLAN §4.0.6). Every type below is an alias of the
 * generated contract — never a re-declaration of it (PLAN §4.2.6-A, `docs/AGENT-HARNESS.md` §4).
 *
 * Pure async functions, no React (PLAN §4.2.6-H). Hooks in `features/admin/hooks/` call these.
 *
 * <b>Accounts only.</b> There is no function here that reads another user's trips, chats, or
 * bookings, and no endpoint to build one on — the contract publishes exactly four admin
 * operations, and none of them returns trip data.
 */

export type AdminUserSummary = components['schemas']['AdminUserSummary'];
export type AdminUserDetail = components['schemas']['AdminUserDetail'];
export type AdminUserPage = components['schemas']['AdminUserPage'];
export type UpdateAdminUserRequest = components['schemas']['UpdateAdminUserRequest'];
export type AdminResetPasswordRequest = components['schemas']['AdminResetPasswordRequest'];

/** Matches `PageQuery.DEFAULT_PAGE_SIZE` on the backend, and the contract's published default. */
export const ADMIN_USERS_PAGE_SIZE = 20;

export interface AdminUserQuery {
  /** Zero-based, exactly as the contract publishes it — not Ant Design's one-based page number. */
  page: number;
  pageSize?: number;
}

/**
 * UC-A15. The endpoint publishes `created_at` as its only sortable field, so the sort is not a
 * parameter here: offering one the server rejects with `400 validation_failed` would be a control
 * that can only produce an error.
 */
export async function fetchAdminUsers(query: AdminUserQuery, signal?: AbortSignal): Promise<AdminUserPage> {
  const search = new URLSearchParams({
    page: String(query.page),
    page_size: String(query.pageSize ?? ADMIN_USERS_PAGE_SIZE),
  });
  return apiRequest({
    // The contract publishes one path; the query string is appended here because `apiRequest`
    // deliberately owns nothing but the base URL, credentials, correlation, and error mapping.
    path: `/admin/users?${search.toString()}` as '/admin/users',
    signal,
    validate: (payload) => adminUserPageSchema.parse(payload),
  });
}

/** UC-A15. `404 user_not_found` for an id that does not resolve. */
export async function fetchAdminUser(userId: string, signal?: AbortSignal): Promise<AdminUserDetail> {
  return apiRequest({
    path: `/admin/users/${userId}` as '/admin/users/{userId}',
    signal,
    validate: (payload) => adminUserDetailSchema.parse(payload),
  });
}

export interface SetAdminUserEnabledCommand {
  userId: string;
  enabled: boolean;
}

/**
 * PLAN §4.0.6. Disabling terminates every session the account holds (ADR 009 §1) — server-side,
 * before this resolves, so the returned detail is already the post-revocation state.
 */
export async function setAdminUserEnabled(command: SetAdminUserEnabledCommand): Promise<AdminUserDetail> {
  return apiRequest({
    path: `/admin/users/${command.userId}` as '/admin/users/{userId}',
    method: 'PUT',
    body: { enabled: command.enabled } satisfies UpdateAdminUserRequest,
    validate: (payload) => adminUserDetailSchema.parse(payload),
  });
}

export interface AdminResetPasswordCommand {
  userId: string;
  newPassword: string;
}

/**
 * UC-A16. `204` with no body: the administrator already knows the value they sent and passes it on
 * out of band, and echoing it back would put a working credential into a response, a browser
 * cache, and any proxy log between the two.
 */
export async function resetAdminUserPassword(command: AdminResetPasswordCommand): Promise<void> {
  await apiRequest<void>({
    path: `/admin/users/${command.userId}/reset-password` as '/admin/users/{userId}/reset-password',
    method: 'PUT',
    body: { new_password: command.newPassword } satisfies AdminResetPasswordRequest,
  });
}
