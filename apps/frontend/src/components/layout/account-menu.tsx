'use client';

import { UserOutlined } from '@ant-design/icons';
import { Button, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useCurrentUser, useSignOut } from '@/features/auth';

/**
 * The account menu §5.1 places user, locale, theme, and sign-out behind.
 *
 * Locale and theme live in Settings rather than being duplicated here as inline switchers: two
 * controls for one preference is two places for them to disagree, and §8.9 already owns them. The
 * menu links there instead.
 *
 * The trigger is a real `Button` with a translated `aria-label`, not an avatar `div` — §3.5
 * requires icon-only controls to carry a translated accessible name, and §6.1 a 44 px hit area.
 */
export function AccountMenu() {
  const t = useTranslations('common');
  const router = useRouter();
  const { data: user } = useCurrentUser();
  const signOut = useSignOut();

  const items: MenuProps['items'] = [
    { key: 'email', label: user?.email ?? '', disabled: true },
    { type: 'divider' },
    { key: 'settings', label: t('nav.settings') },
    { key: 'sign_out', label: t('actions.sign_out'), danger: true },
  ];

  const handleClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'settings') {
      router.push('/settings');
    }
    if (key === 'sign_out') {
      signOut.mutate();
    }
  };

  return (
    <Dropdown menu={{ items, onClick: handleClick }} trigger={['click']} placement="bottomRight">
      <Button
        type="text"
        size="large"
        icon={<UserOutlined />}
        aria-label={t('nav.account_menu')}
        loading={signOut.isPending}
        className="min-h-control min-w-control"
      />
    </Dropdown>
  );
}
