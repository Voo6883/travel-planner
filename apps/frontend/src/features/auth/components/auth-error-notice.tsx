'use client';

import { Alert } from 'antd';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { ErrorAlert } from '@/components/ui/error-alert';
import { ApiError } from '@/lib/api/api-error';
import { providerLabelKey } from '../lib/provider-labels';

export interface AuthErrorNoticeProps {
  error: unknown;
}

/**
 * Authentication failures that have a *specific* recovery, rather than "try again".
 *
 * Two codes earn their own treatment because the generic alert would be actively unhelpful:
 *
 * - **`email_not_verified`** — the password was correct. The account exists and the user owns it;
 *   the only thing missing is a click in their inbox. Sending them back to the same form to retype
 *   a password that already worked is the wrong instruction, so this links to the resend flow.
 *   The endpoint keeps this code distinct from `invalid_credentials` precisely to make this
 *   possible (contract `SignInForbidden`), and it is only ever raised *after* the password
 *   verified — so it reveals nothing to someone who does not already hold the credential.
 * - **`provider_link_required`** — an account already holds this address but auto-linking was
 *   refused because one side was unverified (ADR 009 §4). Nothing was linked, and the user's next
 *   step is to sign in to that account and connect the provider explicitly.
 *
 * Everything else falls through to `ErrorAlert`, which translates `code` and shows the request id.
 */
export function AuthErrorNotice({ error }: AuthErrorNoticeProps) {
  const t = useTranslations('auth');
  const tCommon = useTranslations('common');

  if (!(error instanceof ApiError)) {
    return error === null || error === undefined ? null : <ErrorAlert error={error} />;
  }

  if (error.code === 'email_not_verified') {
    return (
      <Alert
        type="warning"
        showIcon
        role="alert"
        message={t('verification_required_title')}
        description={
          <div className="flex flex-col gap-2">
            <p className="m-0 text-body-sm">{tCommon('errors.email_not_verified')}</p>
            <Link href="/verify-email" className="text-action-primary-text">
              {t('resend_verification_submit')}
            </Link>
          </div>
        }
      />
    );
  }

  if (error.code === 'provider_link_required') {
    const provider = error.linkRequiredProvider;
    return (
      <Alert
        type="warning"
        showIcon
        role="alert"
        message={t('provider_link_required_title')}
        description={t('provider_link_required_body', {
          provider: provider === null ? '' : t(providerLabelKey(provider)),
        })}
      />
    );
  }

  return <ErrorAlert error={error} />;
}
