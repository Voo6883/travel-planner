'use client';

import { Card, Descriptions, Tag } from 'antd';
import { useTranslations } from 'next-intl';
import { ErrorAlert } from '@/components/ui/error-alert';
import { LoadingState } from '@/components/ui/loading-state';
import { LocaleSwitcher } from '@/components/ui/locale-switcher';
import { ThemeSwitcher } from '@/components/ui/theme-switcher';
import type { CurrentUser } from '@/lib/api/auth-api';
import { ChangePasswordCard } from './change-password-card';
import { ConnectedAccountsCard } from './connected-accounts-card';
import { DangerZoneCard } from './danger-zone-card';
import { useCurrentUser } from '../hooks/use-current-user';

/**
 * Account settings (UC-A11, UC-A12, UC-A14; §8.9 sections Profile, Connected accounts,
 * Preferences, Appearance, Account).
 *
 * Everything here is read from `/auth/me` rather than from a sign-in response held in memory:
 * the linked-provider list changes underneath this page whenever a GitHub link round trip
 * completes, and a cached copy would show the user the state from before they left.
 *
 * The screen owns the loading/error branch itself (PLAN §4.2.6-K) instead of relying on the
 * guard, because the guard renders it only once a session exists — a `refetch` failure after that
 * still has to land somewhere.
 */
export function AccountSettingsPanel() {
  const t = useTranslations('auth');
  // Kept as one object rather than destructured: React Query's result is a discriminated union,
  // and destructuring throws away the narrowing that makes `query.data` non-optional below.
  const query = useCurrentUser();

  if (query.isPending) {
    return <LoadingState label={t('settings_title')} rows={5} />;
  }
  if (query.isError) {
    return <ErrorAlert error={query.error} onRetry={() => void query.refetch()} />;
  }

  const user = query.data;

  return (
    <div className="flex flex-col gap-6">
      <ProfileCard user={user} />

      <Card title={t('preferences_section_title')}>
        <div className="flex max-w-form flex-col gap-6">
          <ThemeSwitcher />
          <LocaleSwitcher />
        </div>
      </Card>

      <ConnectedAccountsCard user={user} />
      <ChangePasswordCard />
      <DangerZoneCard email={user.email} />
    </div>
  );
}

function ProfileCard({ user }: { user: CurrentUser }) {
  const t = useTranslations('auth');

  return (
    <Card title={t('profile_section_title')}>
      <Descriptions column={1} size="small" colon={false}>
        <Descriptions.Item label={t('profile_email_label')}>{user.email}</Descriptions.Item>
        <Descriptions.Item label={t('profile_username_label')}>
          {user.username ?? t('profile_username_empty')}
        </Descriptions.Item>
        <Descriptions.Item label={t('profile_roles_label')}>{user.roles.join(', ')}</Descriptions.Item>
        <Descriptions.Item label={t('profile_verified_label')}>
          {/* Icon + text + colour, never colour alone (§10.1). */}
          <Tag color={user.email_verified ? 'success' : 'warning'}>
            {user.email_verified ? t('profile_verified_yes') : t('profile_verified_no')}
          </Tag>
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
}
