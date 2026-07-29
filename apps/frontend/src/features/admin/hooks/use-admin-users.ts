'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from '@tanstack/react-query';
import {
  fetchAdminUser,
  fetchAdminUsers,
  resetAdminUserPassword,
  setAdminUserEnabled,
  type AdminResetPasswordCommand,
  type AdminUserDetail,
  type AdminUserPage,
  type SetAdminUserEnabledCommand,
} from '@/lib/api/admin-api';
import { queryKeys } from '@/lib/query/query-keys';

/**
 * The four admin operations, as React Query hooks (UC-A15, UC-A16).
 *
 * <b>Both mutations invalidate the whole `admin.users` subtree.</b> Disabling an account changes
 * the row in the list *and* the detail screen, and a reset changes neither field visibly but does
 * change `updated_at` — invalidating only the record that was edited would leave an administrator
 * acting on a list the server no longer agrees with, which on a destructive surface is how the
 * wrong account gets switched off.
 *
 * No optimistic updates. The server refuses several of these (`account_closed`, self-disable), and
 * an optimistic row that flips and then flips back reads as a bug rather than as a refusal.
 */

export function useAdminUsers(page: number): UseQueryResult<AdminUserPage, unknown> {
  return useQuery({
    queryKey: queryKeys.admin.userPage(page),
    queryFn: ({ signal }) => fetchAdminUsers({ page }, signal),
    // Paging with `placeholderData` would keep the previous page visible while the next loads,
    // but §6.8 asks for a skeleton matching the final layout on an initial load and for existing
    // content to remain on a background refresh — which is what React Query does here by default.
  });
}

export function useAdminUser(userId: string): UseQueryResult<AdminUserDetail, unknown> {
  return useQuery({
    queryKey: queryKeys.admin.user(userId),
    queryFn: ({ signal }) => fetchAdminUser(userId, signal),
  });
}

export function useSetAdminUserEnabled(): UseMutationResult<AdminUserDetail, unknown, SetAdminUserEnabledCommand> {
  const invalidate = useAdminUsersInvalidation();

  return useMutation({
    mutationFn: setAdminUserEnabled,
    onSuccess: invalidate,
  });
}

export function useResetAdminUserPassword(): UseMutationResult<void, unknown, AdminResetPasswordCommand> {
  const invalidate = useAdminUsersInvalidation();

  return useMutation({
    mutationFn: resetAdminUserPassword,
    onSuccess: invalidate,
  });
}

function useAdminUsersInvalidation(): () => void {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin.users() });
  };
}
