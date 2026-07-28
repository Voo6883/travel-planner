'use client';

import { Alert, Button, Form, Input } from 'antd';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { ApiError } from '@/lib/api/api-error';
import { AuthCard } from './auth-card';
import { AuthErrorNotice } from './auth-error-notice';
import { useResetPassword } from '../hooks/use-password-reset';
import { confirmPasswordRules, newPasswordRules } from '../lib/form-rules';
import { newPasswordSchema } from '../schemas/credentials.schema';
import type { ResetPasswordFormValues } from '../types';

/**
 * Set a new password from the mailed link (UC-A07).
 *
 * §8.2 asks this screen to handle "valid, expired, and already-used links with a clear next
 * action". The contract collapses those three into one `invalid_token` on purpose — telling
 * "expired" apart from "never existed" would confirm that a token was once issued, which is a
 * fact about somebody else's mailbox — so all three land on the same recovery: request a fresh
 * link.
 *
 * The form stays mounted after a `validation_failed`, which is a different case sharing the same
 * `400`: there the *password* was rejected and **the token was not spent**, so the same link
 * still works and the user only has to pick a longer password.
 */
export function ResetPasswordPanel() {
  const t = useTranslations('auth');
  const [form] = Form.useForm<ResetPasswordFormValues>();
  const token = useSearchParams().get('token');
  const resetPassword = useResetPassword({ token: token ?? '' });

  if (token === null || token === '') {
    return <MissingTokenCard />;
  }

  if (resetPassword.isSuccess) {
    return (
      <AuthCard
        title={t('reset_password_success_title')}
        description={t('reset_password_success_body')}
      >
        <Alert type="success" showIcon role="status" message={t('reset_password_success_title')} />
        <Link
          href="/sign-in"
          className="mt-6 inline-flex min-h-control items-center text-action-primary-text"
        >
          {t('go_to_sign_in')}
        </Link>
      </AuthCard>
    );
  }

  const isSpentToken =
    resetPassword.error instanceof ApiError && resetPassword.error.code === 'invalid_token';

  return (
    <AuthCard title={t('reset_password_title')} description={t('reset_password_body')}>
      {resetPassword.error ? (
        <div className="mb-4 flex flex-col gap-3">
          <AuthErrorNotice error={resetPassword.error} />
          {isSpentToken ? (
            <Link href="/forgot-password" className="text-action-primary-text">
              {t('request_new_link')}
            </Link>
          ) : null}
        </div>
      ) : null}

      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        disabled={resetPassword.isPending}
        onFinish={(values: ResetPasswordFormValues) =>
          resetPassword.mutate(newPasswordSchema.parse(values))
        }
      >
        <Form.Item
          name="new_password"
          label={t('new_password_label')}
          extra={t('password_help')}
          rules={newPasswordRules(t)}
        >
          <Input.Password size="large" autoComplete="new-password" />
        </Form.Item>

        <Form.Item
          name="confirm_password"
          label={t('confirm_password_label')}
          dependencies={['new_password']}
          rules={confirmPasswordRules(t, 'new_password')}
        >
          <Input.Password size="large" autoComplete="new-password" />
        </Form.Item>

        <Button
          type="primary"
          size="large"
          block
          htmlType="submit"
          loading={resetPassword.isPending}
        >
          {t('reset_password_submit')}
        </Button>
      </Form>
    </AuthCard>
  );
}

/** A link opened without `?token=` — usually a mail client that truncated the URL. */
function MissingTokenCard() {
  const t = useTranslations('auth');

  return (
    <AuthCard title={t('missing_token_title')} description={t('missing_token_body')}>
      <Link
        href="/forgot-password"
        className="inline-flex min-h-control items-center text-action-primary-text"
      >
        {t('request_new_link')}
      </Link>
    </AuthCard>
  );
}
