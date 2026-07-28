'use client';

import { Button, Card, Input, Modal } from 'antd';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { AuthErrorNotice } from './auth-error-notice';
import { useDeleteAccount } from '../hooks/use-account-management';
import { useSignOutEverywhere } from '../hooks/use-sign-out';

export interface DangerZoneCardProps {
  email: string;
}

/**
 * Sign out everywhere and delete account (UC-A14, ADR 009 §5, §8.9).
 *
 * Deletion is typed-confirmation rather than a plain OK. §11.2 asks confirmation copy to name the
 * action and its consequence, and §6.6 asks a destructive confirmation to name the affected item;
 * retyping the address makes an accidental double-click impossible and forces the user to read
 * which account they are about to erase.
 *
 * It is a soft delete with PII anonymisation on the server — the row survives so trips and audit
 * records keep their references — but from the user's side it is irreversible, and the copy says
 * that rather than hinting at a recovery path that does not exist.
 */
export function DangerZoneCard({ email }: DangerZoneCardProps) {
  const t = useTranslations('auth');
  const signOutEverywhere = useSignOutEverywhere();
  const deleteAccount = useDeleteAccount();
  const [isConfirming, setIsConfirming] = useState(false);
  const [confirmation, setConfirmation] = useState('');
  const matches = confirmation.trim().toLowerCase() === email.toLowerCase();

  return (
    <Card title={t('security_section_title')}>
      <section className="flex flex-col gap-2">
        <h3 className="m-0 text-title text-foreground">{t('sign_out_everywhere_title')}</h3>
        <p className="m-0 text-body-sm text-foreground-muted">{t('sign_out_everywhere_body')}</p>
        {signOutEverywhere.error ? <AuthErrorNotice error={signOutEverywhere.error} /> : null}
        <Button
          size="large"
          className="w-fit"
          loading={signOutEverywhere.isPending}
          onClick={() => signOutEverywhere.mutate()}
        >
          {t('sign_out_everywhere_submit')}
        </Button>
      </section>

      <section className="mt-8 flex flex-col gap-2 rounded-lg border border-destructive bg-destructive-surface p-4">
        <h3 className="m-0 text-title text-foreground">{t('danger_zone_title')}</h3>
        <p className="m-0 text-body-sm text-foreground-muted">{t('danger_zone_body')}</p>
        {deleteAccount.error ? <AuthErrorNotice error={deleteAccount.error} /> : null}
        <Button danger size="large" className="w-fit" onClick={() => setIsConfirming(true)}>
          {t('delete_account_submit')}
        </Button>
      </section>

      <Modal
        open={isConfirming}
        title={t('delete_account_confirm_title')}
        okText={t('delete_account_submit')}
        okButtonProps={{ danger: true, disabled: !matches, loading: deleteAccount.isPending }}
        onOk={() => deleteAccount.mutate()}
        onCancel={() => setIsConfirming(false)}
      >
        <div className="flex flex-col gap-3">
          <p className="m-0 text-body-sm">{t('delete_account_confirm_body')}</p>
          <label className="flex flex-col gap-2 text-label">
            {t('delete_account_confirm_label')}
            <Input
              size="large"
              value={confirmation}
              autoComplete="off"
              onChange={(event) => setConfirmation(event.target.value)}
            />
          </label>
          {confirmation !== '' && !matches ? (
            <p className="m-0 text-body-sm text-destructive">
              {t('delete_account_confirm_mismatch')}
            </p>
          ) : null}
        </div>
      </Modal>
    </Card>
  );
}
