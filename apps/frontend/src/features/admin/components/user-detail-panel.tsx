'use client';

import { Alert, Button, Card, Descriptions, Modal } from 'antd';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { ErrorAlert } from '@/components/ui/error-alert';
import { LoadingState } from '@/components/ui/loading-state';
import type { AdminUserDetail } from '@/lib/api/admin-api';
import { AccountStatusTag } from './account-status-tag';
import { ResetPasswordModal } from './reset-password-modal';
import { useAdminUser, useSetAdminUserEnabled } from '../hooks/use-admin-users';
import { formatIsoDate } from '../lib/admin-format';

export interface UserDetailPanelProps {
  userId: string;
}

/**
 * One account, and the two things an administrator may do to it (UC-A15, UC-A16, §8.10).
 *
 * §8.10 fixes the shape: "user detail uses description groups; password reset is a dedicated
 * modal". The description list is the whole of the account state this surface publishes — there is
 * no trip, chat, or booking data here, and no endpoint to fetch any.
 *
 * Both actions confirm explicitly (§6.6), name the affected account, and say what will happen to
 * that account's sessions before the administrator commits. Disabling ends every session
 * immediately (ADR 009 §1); enabling does not restore the ones that were ended. Neither is
 * obvious, and being surprised by either is how an administrator loses trust in the screen.
 */
export function UserDetailPanel({ userId }: UserDetailPanelProps) {
  const t = useTranslations('admin');
  const query = useAdminUser(userId);

  if (query.isPending) {
    return <LoadingState label={t('detail.loading')} rows={6} />;
  }

  if (query.isError) {
    return <ErrorAlert error={query.error} onRetry={() => void query.refetch()} />;
  }

  return <LoadedDetail account={query.data} />;
}

function LoadedDetail({ account }: { account: AdminUserDetail }) {
  const t = useTranslations('admin');
  const setEnabled = useSetAdminUserEnabled();
  const [confirmingStatus, setConfirmingStatus] = useState(false);
  const [resetting, setResetting] = useState(false);

  return (
    <div className="flex flex-col gap-6">
      <Link href="/admin/users" className="text-body-sm">
        {t('detail.back_to_list')}
      </Link>

      {account.closed ? (
        // The server refuses every mutation against a closed account with `409 account_closed`.
        // Saying so up front is what stops the actions below from reading as broken.
        <Alert type="info" showIcon message={t('detail.closed_notice')} />
      ) : null}

      {setEnabled.isSuccess ? (
        <Alert
          type="success"
          showIcon
          role="status"
          message={
            account.enabled ? t('status_action.enabled_success') : t('status_action.disabled_success')
          }
        />
      ) : null}

      {setEnabled.error ? <ErrorAlert error={setEnabled.error} /> : null}

      <Card title={t('detail.title')}>
        <Descriptions column={{ xs: 1, md: 2 }} size="small" bordered>
          <Descriptions.Item label={t('field.email')}>{account.email}</Descriptions.Item>
          <Descriptions.Item label={t('field.username')}>
            {account.username ?? t('field.username_absent')}
          </Descriptions.Item>
          <Descriptions.Item label={t('field.status')}>
            <AccountStatusTag account={account} />
          </Descriptions.Item>
          <Descriptions.Item label={t('field.email_verified')}>
            {account.email_verified ? t('field.verified_yes') : t('field.verified_no')}
          </Descriptions.Item>
          <Descriptions.Item label={t('field.roles')}>{account.roles.join(', ')}</Descriptions.Item>
          <Descriptions.Item label={t('field.sign_in_methods')}>
            {signInMethods(account, t)}
          </Descriptions.Item>
          <Descriptions.Item label={t('field.created_at')}>
            {formatIsoDate(account.created_at)}
          </Descriptions.Item>
          <Descriptions.Item label={t('field.updated_at')}>
            {formatIsoDate(account.updated_at)}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={t('actions.title')}>
        <p className="mb-4 mt-0 text-body-sm text-foreground-muted">{t('actions.body')}</p>
        <div className="flex flex-wrap gap-3">
          <Button
            danger={account.enabled}
            disabled={account.closed}
            loading={setEnabled.isPending}
            onClick={() => setConfirmingStatus(true)}
          >
            {account.enabled ? t('status_action.disable') : t('status_action.enable')}
          </Button>
          <Button
            // A provider-only account has no local password to replace (ADR 009 §4), and the
            // server refuses with `validation_failed`. Disabling the control is a courtesy that
            // avoids inviting a click that cannot succeed; the server is still the rule.
            disabled={account.closed || !account.has_local_password}
            onClick={() => setResetting(true)}
          >
            {t('reset.open_action')}
          </Button>
        </div>
        {account.has_local_password ? null : (
          <p className="mb-0 mt-3 text-caption text-foreground-muted">
            {t('reset.unavailable_provider_only')}
          </p>
        )}
      </Card>

      <Modal
        open={confirmingStatus}
        title={
          account.enabled
            ? t('status_action.disable_confirm_title', { email: account.email })
            : t('status_action.enable_confirm_title', { email: account.email })
        }
        okText={account.enabled ? t('status_action.disable') : t('status_action.enable')}
        okButtonProps={{ danger: account.enabled, loading: setEnabled.isPending }}
        onOk={() => {
          setEnabled.mutate({ userId: account.user_id, enabled: !account.enabled });
          setConfirmingStatus(false);
        }}
        onCancel={() => setConfirmingStatus(false)}
      >
        <p className="m-0 text-body-sm">
          {account.enabled
            ? t('status_action.disable_confirm_body')
            : t('status_action.enable_confirm_body')}
        </p>
      </Modal>

      <ResetPasswordModal
        account={account}
        open={resetting}
        onClose={() => setResetting(false)}
      />
    </div>
  );
}

function signInMethods(
  account: AdminUserDetail,
  t: ReturnType<typeof useTranslations<'admin'>>,
): string {
  const methods = [...account.linked_providers];
  // `password_hash IS NOT NULL` is what actually lets an account sign in locally; the bookkeeping
  // LOCAL identity row may be absent even when a password is set (ADR 009 §4).
  if (account.has_local_password && !methods.includes('LOCAL')) {
    methods.unshift('LOCAL');
  }
  return methods.length === 0 ? t('field.sign_in_methods_none') : methods.join(', ');
}
