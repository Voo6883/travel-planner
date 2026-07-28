'use client';

import { Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorAlert } from '@/components/ui/error-alert';
import { LoadingState } from '@/components/ui/loading-state';
import type { AdminUserSummary } from '@/lib/api/admin-api';
import { AccountStatusTag } from './account-status-tag';
import { useAdminUsers } from '../hooks/use-admin-users';
import { adminUserRoute, formatIsoDate } from '../lib/admin-format';

/**
 * The account list (UC-A15, §8.10).
 *
 * §6.7 governs the shape: an Ant Design Table rather than cards, the primary identifier — the
 * email address — in the first column, and the row action last. §8.10 asks for compact density
 * "while preserving 44 px controls and 48 px rows", so the table stays at Ant's `middle` size,
 * which is the compact step that still clears 48 px; `small` would not.
 *
 * Below `md` the secondary columns drop out rather than being squeezed (§6.7 "do not squeeze
 * columns"): what remains is the address, the status, and the way in, which is the minimum from
 * which an administrator can still act.
 *
 * §6.8's states are all present and each is a different answer: a skeleton on first load, the
 * shared `ErrorAlert` with a retry, and an `EmptyState` for a genuinely empty page — which is not
 * the same thing as a failed request, and must not look like one.
 */
export function UserListPanel() {
  // Zero-based, matching the contract. Ant's pagination is one-based, and the conversion is done
  // in exactly one place below so the two conventions cannot drift into each other.
  const [page, setPage] = useState(0);
  const t = useTranslations('admin');
  const query = useAdminUsers(page);

  if (query.isPending) {
    return <LoadingState label={t('list.loading')} rows={6} />;
  }

  if (query.isError) {
    return <ErrorAlert error={query.error} onRetry={() => void query.refetch()} />;
  }

  if (query.data.total === 0) {
    return <EmptyState title={t('list.empty_title')} description={t('list.empty_body')} />;
  }

  return (
    <div className="flex flex-col gap-3">
      <p aria-live="polite" className="m-0 text-body-sm text-foreground-muted">
        {t('list.result_count', { total: query.data.total })}
      </p>
      <Table<AdminUserSummary>
        size="middle"
        rowKey="user_id"
        columns={columns(t)}
        dataSource={query.data.items}
        // Horizontal scroll rather than compression: §6.7 forbids squeezing columns, and a table
        // that scrolls is readable where one that has been crushed is not.
        scroll={{ x: 'max-content' }}
        pagination={{
          current: query.data.page + 1,
          pageSize: query.data.page_size,
          total: query.data.total,
          showSizeChanger: false,
          onChange: (next) => setPage(next - 1),
        }}
      />
    </div>
  );
}

type Translate = ReturnType<typeof useTranslations<'admin'>>;

function columns(t: Translate): ColumnsType<AdminUserSummary> {
  return [
    {
      title: t('field.email'),
      dataIndex: 'email',
      key: 'email',
      render: (email: string, account) => (
        // The identifier is also the way in — §6.7 puts it first, and making it the link means the
        // largest, most obvious target on the row is the safe, non-destructive action.
        <Link href={adminUserRoute(account.user_id)} className="text-body-sm">
          {email}
        </Link>
      ),
    },
    {
      title: t('field.username'),
      dataIndex: 'username',
      key: 'username',
      responsive: ['md'],
      render: (username: string | null | undefined) => username ?? t('field.username_absent'),
    },
    {
      title: t('field.status'),
      key: 'status',
      render: (_, account) => <AccountStatusTag account={account} />,
    },
    {
      title: t('field.email_verified'),
      dataIndex: 'email_verified',
      key: 'email_verified',
      responsive: ['lg'],
      render: (verified: boolean) => (
        <Tag color={verified ? 'success' : 'warning'}>
          {verified ? t('field.verified_yes') : t('field.verified_no')}
        </Tag>
      ),
    },
    {
      title: t('field.roles'),
      dataIndex: 'roles',
      key: 'roles',
      responsive: ['lg'],
      render: (roles: string[]) => roles.join(', '),
    },
    {
      title: t('field.created_at'),
      dataIndex: 'created_at',
      key: 'created_at',
      responsive: ['xl'],
      render: (createdAt: string) => formatIsoDate(createdAt),
    },
    {
      title: t('field.actions'),
      key: 'actions',
      render: (_, account) => (
        <Link
          href={adminUserRoute(account.user_id)}
          className="inline-flex min-h-control items-center text-body-sm"
        >
          {t('list.manage_action')}
        </Link>
      ),
    },
  ];
}
