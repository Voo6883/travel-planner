'use client';

import { GoogleOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { useTranslations } from 'next-intl';
import { ErrorAlert } from '@/components/ui/error-alert';
import { isFirebaseConfigured } from '../lib/firebase-client';
import { GooglePopupBlocked, GooglePopupCancelled, useGoogleAuth } from '../hooks/use-google-auth';
import type { AuthProviderButtonsProps } from '../types';

/**
 * The shared Google control (PLAN §4.0.5: "one button, two outcomes").
 *
 * The **same** button appears on sign-in and sign-up; only the label changes, because the
 * endpoint behind it genuinely does not care which page it was pressed on — the server decides
 * whether the token means "create", "sign in", or "link". Two separate components would be two
 * places for that behaviour to drift.
 *
 * When Firebase is not configured the button renders disabled with adjacent explanatory text
 * rather than disappearing: §6.1 requires a disabled workflow action to say why, and a control
 * that vanishes reads as a broken page.
 */
export function GoogleSignInButton({ mode }: AuthProviderButtonsProps) {
  const t = useTranslations('auth');
  const { mutate, isPending, error } = useGoogleAuth();
  const configured = isFirebaseConfigured();

  return (
    <div className="flex flex-col gap-2">
      <Button
        block
        size="large"
        // Ant icons default to `role="img"` with the icon's own name as the label, which would
        // make the button announce as "google Continue with Google". The text carries the meaning.
        icon={<GoogleOutlined aria-hidden="true" />}
        loading={isPending}
        disabled={!configured}
        onClick={() => mutate()}
      >
        {mode === 'sign_up' ? t('sign_up_with_google') : t('continue_with_google')}
      </Button>

      {configured ? null : (
        <p className="m-0 text-caption text-foreground-subtle">{t('google_unavailable')}</p>
      )}

      <GoogleAuthFailure error={error} />
    </div>
  );
}

/**
 * A closed pop-up is a decision, not a fault, so it gets a quiet line rather than a red alert.
 * A blocked pop-up needs a different instruction from "try again" — retrying does nothing until
 * the browser setting changes. Everything else is a real API failure and goes to `ErrorAlert`.
 */
function GoogleAuthFailure({ error }: { error: unknown }) {
  const t = useTranslations('auth');

  if (error === null || error === undefined) {
    return null;
  }
  if (error instanceof GooglePopupCancelled) {
    return (
      <p role="status" className="m-0 text-caption text-foreground-muted">
        {t('google_cancelled')}
      </p>
    );
  }
  if (error instanceof GooglePopupBlocked) {
    return (
      <p role="alert" className="m-0 text-body-sm text-warning">
        {t('google_popup_blocked')}
      </p>
    );
  }
  return <ErrorAlert error={error} />;
}
