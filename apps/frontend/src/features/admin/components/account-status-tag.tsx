'use client';

import { Tag } from 'antd';
import { useTranslations } from 'next-intl';
import type { AdminUserSummary } from '@/lib/api/admin-api';

export interface AccountStatusTagProps {
  account: Pick<AdminUserSummary, 'enabled' | 'closed'>;
}

/**
 * The three states an account can be in, as one tag (§10.1: colour never carries the meaning
 * alone — the tag always spells the state out).
 *
 * `closed` is checked first because a closed account also has `enabled: false`, and showing it as
 * merely "disabled" would invite an administrator to try switching it back on — an action the
 * server refuses with `409 account_closed`. Distinguishing them here is what stops the UI from
 * offering a button that cannot work.
 */
export function AccountStatusTag({ account }: AccountStatusTagProps) {
  const t = useTranslations('admin');

  if (account.closed) {
    return <Tag color="default">{t('status.closed')}</Tag>;
  }
  return account.enabled ? (
    <Tag color="success">{t('status.enabled')}</Tag>
  ) : (
    <Tag color="error">{t('status.disabled')}</Tag>
  );
}
