'use client';

import { Alert, Button, Form, Input } from 'antd';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { AuthCard } from './auth-card';
import { AuthErrorNotice } from './auth-error-notice';
import { useForgotPassword } from '../hooks/use-account-mail';
import { emailRules } from '../lib/form-rules';
import { emailOnlySchema } from '../schemas/credentials.schema';
import type { EmailFormValues } from '../types';

/**
 * Request a password-reset link (UC-A07).
 *
 * §8.2: "submission shows a neutral confirmation regardless of whether the account exists". The
 * backend already guarantees a constant `202` for a registered address, an unregistered one, and
 * a provider-only account with no local password — this screen must not undo that by rendering
 * anything conditional. There is nothing to branch on and nothing to reveal.
 *
 * A `429 rate_limited` is the only visible failure, and it is reachable for an unregistered
 * address exactly as for a registered one, so hitting it still tells an attacker nothing.
 */
export function ForgotPasswordPanel() {
  const t = useTranslations('auth');
  const [form] = Form.useForm<EmailFormValues>();
  const forgotPassword = useForgotPassword();

  if (forgotPassword.isSuccess) {
    return (
      <AuthCard title={t('forgot_password_sent_title')} description={t('forgot_password_sent_body')}>
        <Alert type="success" showIcon role="status" message={t('forgot_password_sent_title')} />
        <Link
          href="/sign-in"
          className="mt-6 inline-flex min-h-control items-center text-action-primary-text"
        >
          {t('go_to_sign_in')}
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title={t('forgot_password_title')}
      description={t('forgot_password_body')}
      footer={
        <Link href="/sign-in" className="text-action-primary-text">
          {t('go_to_sign_in')}
        </Link>
      }
    >
      {forgotPassword.error ? (
        <div className="mb-4">
          <AuthErrorNotice error={forgotPassword.error} />
        </div>
      ) : null}

      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        disabled={forgotPassword.isPending}
        onFinish={(values: EmailFormValues) =>
          forgotPassword.mutate(emailOnlySchema.parse(values))
        }
      >
        <Form.Item name="email" label={t('email_label')} rules={emailRules(t)}>
          <Input
            size="large"
            autoComplete="email"
            inputMode="email"
            placeholder={t('email_placeholder')}
          />
        </Form.Item>

        <Button
          type="primary"
          size="large"
          block
          htmlType="submit"
          loading={forgotPassword.isPending}
        >
          {t('forgot_password_submit')}
        </Button>
      </Form>
    </AuthCard>
  );
}
