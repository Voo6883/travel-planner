'use client';

import { Alert, Button } from 'antd';
import { useTranslations } from 'next-intl';
import { AuthErrorNotice } from './auth-error-notice';
import { useResendVerification } from '../hooks/use-account-mail';
import { useSignOut } from '../hooks/use-sign-out';

export interface VerificationGateProps {
  email: string;
}

/**
 * What an authenticated but unverified local account sees instead of the planner (UC-A08, §8.2).
 *
 * §8.2 spells out what it must contain: "a verification gate with email address, resend state,
 * change-account action, and support path". The address is shown because a typo at sign-up is
 * the single most common reason the mail never arrives, and a user who cannot see what they
 * typed cannot diagnose it.
 *
 * The address comes from `/auth/me`, so this is the one resend call that does not need an email
 * input — the session already identifies the account. It is still the same rate-limited,
 * constant-response endpoint.
 *
 * Gmail and GitHub accounts never reach this screen: their addresses arrive provider-verified.
 */
export function VerificationGate({ email }: VerificationGateProps) {
  const t = useTranslations('auth');
  const resend = useResendVerification();
  const signOut = useSignOut();

  return (
    <div className="mx-auto w-full max-w-form px-4 py-10 md:px-6">
      <main id="main-content" className="rounded-lg border border-border-subtle bg-surface p-6">
        <h1 className="m-0 text-h1-mobile text-foreground sm:text-h1">
          {t('verification_required_title')}
        </h1>
        <p className="mb-0 mt-2 text-body-sm text-foreground-muted">
          {t('verification_required_body', { email })}
        </p>
        <p className="mb-0 mt-2 text-caption text-foreground-subtle">
          {t('verification_required_hint')}
        </p>

        <div className="mt-6 flex flex-col gap-3">
          {resend.isSuccess ? (
            <Alert type="success" showIcon role="status" message={t('resend_verification_sent')} />
          ) : null}
          {resend.error ? <AuthErrorNotice error={resend.error} /> : null}

          <Button
            type="primary"
            size="large"
            loading={resend.isPending}
            disabled={resend.isSuccess}
            onClick={() => resend.mutate({ email })}
          >
            {t('resend_verification_submit')}
          </Button>

          <Button size="large" loading={signOut.isPending} onClick={() => signOut.mutate()}>
            {t('use_another_account')}
          </Button>
        </div>
      </main>
    </div>
  );
}
