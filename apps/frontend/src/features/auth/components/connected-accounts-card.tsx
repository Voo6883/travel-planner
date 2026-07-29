'use client';

import { Button, Card, Modal, Tag } from 'antd';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import type { CurrentUser, IdentityProvider } from '@/lib/api/auth-api';
import { AuthErrorNotice } from './auth-error-notice';
import { GithubSignInButton } from './github-sign-in-button';
import { useUnlinkProvider } from '../hooks/use-account-management';
import { providerLabelKey } from '../lib/provider-labels';

const ALL_PROVIDERS: readonly IdentityProvider[] = ['LOCAL', 'FIREBASE_GOOGLE', 'GITHUB'];

export interface ConnectedAccountsCardProps {
  user: CurrentUser;
}

/**
 * Sign-in methods on this account (UC-A09, UC-A11, §8.9 "Connected accounts").
 *
 * Disconnecting is refused server-side when it would leave the account with no way in
 * (`409 last_sign_in_method`), and the button is disabled for that case too — but the disable is
 * a courtesy, not the rule. The server decides; this only avoids inviting a click that cannot
 * succeed, which §6.1 asks to pair with adjacent explanatory text rather than a tooltip.
 *
 * Unlinking ends every session (ADR 009 §1): the case it exists for is "that provider account is
 * no longer mine", which is exactly when sessions obtained through it must stop working now. So
 * the confirmation says so, and success lands on sign-in rather than back here.
 *
 * Connecting GitHub is a redirect (`?mode=link`), never a request body — its credential is a
 * single-use authorization code the browser never holds.
 */
export function ConnectedAccountsCard({ user }: ConnectedAccountsCardProps) {
  const t = useTranslations('auth');
  const unlink = useUnlinkProvider();
  const [pendingUnlink, setPendingUnlink] = useState<IdentityProvider | null>(null);

  return (
    <Card title={t('connected_accounts_title')}>
      <p className="mb-4 mt-0 text-body-sm text-foreground-muted">{t('connected_accounts_body')}</p>

      {unlink.error ? (
        <div className="mb-4">
          <AuthErrorNotice error={unlink.error} />
        </div>
      ) : null}

      <ul className="m-0 flex list-none flex-col gap-3 p-0">
        {ALL_PROVIDERS.map((provider) => (
          <li
            key={provider}
            className="flex flex-wrap items-center justify-between gap-3 border-b border-border-subtle pb-3 last:border-b-0 last:pb-0"
          >
            <div className="flex flex-col gap-1">
              <span className="text-label text-foreground">{t(providerLabelKey(provider))}</span>
              <ProviderStatusTag isConnected={user.linked_providers.includes(provider)} />
            </div>
            <ProviderAction provider={provider} user={user} onDisconnect={() => setPendingUnlink(provider)} />
          </li>
        ))}
      </ul>

      <Modal
        open={pendingUnlink !== null}
        title={
          pendingUnlink === null
            ? ''
            : t('provider_disconnect_confirm_title', { provider: t(providerLabelKey(pendingUnlink)) })
        }
        okText={t('provider_disconnect_action')}
        okButtonProps={{ danger: true, loading: unlink.isPending }}
        onOk={() => (pendingUnlink === null ? undefined : unlink.mutate(pendingUnlink))}
        onCancel={() => setPendingUnlink(null)}
      >
        <p className="m-0 text-body-sm">{t('provider_disconnect_confirm_body')}</p>
      </Modal>
    </Card>
  );
}

function ProviderStatusTag({ isConnected }: { isConnected: boolean }) {
  const t = useTranslations('auth');

  // §10.1: colour never carries the meaning alone — the tag always spells the state out.
  return (
    <Tag color={isConnected ? 'success' : 'default'} className="w-fit">
      {isConnected ? t('provider_connected') : t('provider_not_connected')}
    </Tag>
  );
}

interface ProviderActionProps {
  provider: IdentityProvider;
  user: CurrentUser;
  onDisconnect: () => void;
}

function ProviderAction({ provider, user, onDisconnect }: ProviderActionProps) {
  const t = useTranslations('auth');
  const isConnected = user.linked_providers.includes(provider);

  if (!isConnected) {
    // Only GitHub can be connected from here. Google needs a fresh Firebase popup token, which is
    // the sign-in button's job, and LOCAL is a password, not a link.
    return provider === 'GITHUB' ? (
      <GithubSignInButton mode="sign_in" flow="link" label={t('provider_connect_action')} />
    ) : null;
  }

  return (
    <Button
      danger
      // A single remaining method is the account's only way in (ADR 009 §4).
      disabled={user.linked_providers.length <= 1}
      onClick={onDisconnect}
    >
      {t('provider_disconnect_action')}
    </Button>
  );
}
