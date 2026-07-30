'use client';

import { UserOutlined } from '@ant-design/icons';
import { Button, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

export interface AccountMenuProps {
  /** The signed-in address, shown as the menu's disabled first row. Empty until it is known. */
  readonly email: string;
  readonly onSignOut: () => void;
  /** Renders the trigger's spinner while the sign-out request is in flight. */
  readonly signOutPending?: boolean;
}

/**
 * The account menu §5.1 places user, locale, theme, and sign-out behind.
 *
 * Locale and theme live in Settings rather than being duplicated here as inline switchers: two
 * controls for one preference is two places for them to disagree, and §8.9 already owns them. The
 * menu links there instead.
 *
 * The trigger is a real `Button` with a translated `aria-label`, not an avatar `div` — §3.5
 * requires icon-only controls to carry a translated accessible name, and §6.1 a 44 px hit area.
 *
 * <b>Identity arrives as props.</b> This component used to call `useCurrentUser` and `useSignOut`
 * from `@/features/auth`, which made shared chrome depend on a feature — the wrong direction, and it
 * needed an ESLint exception to compile. `features/auth/components/authenticated-app-shell.tsx` owns
 * that coupling now. See the note there for why inversion was the fix rather than a wider rule.
 */
export function AccountMenu({ email, onSignOut, signOutPending = false }: AccountMenuProps) {
  const t = useTranslations('common');
  const router = useRouter();

  const items: MenuProps['items'] = [
    { key: 'email', label: email, disabled: true },
    { type: 'divider' },
    { key: 'settings', label: t('nav.settings') },
    { key: 'sign_out', label: t('actions.sign_out'), danger: true },
  ];

  const handleClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'settings') {
      router.push('/settings');
    }
    if (key === 'sign_out') {
      onSignOut();
    }
  };

  return (
    <Dropdown menu={{ items, onClick: handleClick }} trigger={['click']} placement="bottomRight">
      <Button
        type="text"
        size="large"
        icon={<UserOutlined />}
        aria-label={t('nav.account_menu')}
        loading={signOutPending}
        className="min-h-control min-w-control"
      />
    </Dropdown>
  );
}
