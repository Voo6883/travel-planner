'use client';

import { Alert, Button, Form, Input } from 'antd';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { AuthCard } from './auth-card';
import { AuthErrorNotice } from './auth-error-notice';
import { AuthProviderButtons } from './auth-provider-buttons';
import { useSignUp } from '../hooks/use-sign-up';
import { emailRules, newPasswordRules, usernameRules } from '../lib/form-rules';
import { signUpSchema } from '../schemas/credentials.schema';
import type { SignUpFormValues } from '../types';

/**
 * Local sign-up (UC-A01).
 *
 * The success state deliberately says nothing about the account. `202 PENDING_VERIFICATION` is
 * returned whether the account was created, the address was already registered, or the username
 * was taken (ADR 009 §6) — so this screen shows one message in all three cases. Any variation
 * here (a "welcome back", a different heading for an existing address) would answer "does this
 * account exist?" for anyone who asked, which is exactly what the constant response prevents.
 *
 * Field-level validation failures are shown normally: a malformed email or a short password is
 * the caller's own input and reveals nothing about anyone else.
 */
export function SignUpPanel() {
  const t = useTranslations('auth');
  const [form] = Form.useForm<SignUpFormValues>();
  const signUp = useSignUp();
  const [dirtySinceError, setDirtySinceError] = useState(false);

  if (signUp.isSuccess) {
    return (
      <AuthCard title={t('sign_up_pending_title')} description={t('sign_up_pending_body')}>
        <Alert type="success" showIcon role="status" message={t('sign_up_pending_title')} />
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
      title={t('sign_up_title')}
      description={t('sign_up_subtitle')}
      footer={
        <span className="text-foreground-muted">
          {t('have_account_question')}{' '}
          <Link href="/sign-in" className="text-action-primary-text">
            {t('go_to_sign_in')}
          </Link>
        </span>
      }
    >
      <AuthProviderButtons mode="sign_up" />

      {signUp.error && !dirtySinceError ? (
        <div className="mb-4">
          <AuthErrorNotice error={signUp.error} />
        </div>
      ) : null}

      <p className="mb-4 mt-0 text-caption text-foreground-subtle">{t('required_fields_note')}</p>

      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        disabled={signUp.isPending}
        onValuesChange={() => setDirtySinceError(true)}
        onFinish={(values: SignUpFormValues) => signUp.mutate(signUpSchema.parse(values))}
      >
        <Form.Item name="email" label={t('email_label')} rules={emailRules(t)}>
          <Input size="large" autoComplete="email" inputMode="email" placeholder={t('email_placeholder')} />
        </Form.Item>

        <Form.Item
          name="username"
          label={t('username_label')}
          extra={t('username_help')}
          rules={usernameRules(t)}
        >
          <Input size="large" autoComplete="username" />
        </Form.Item>

        <Form.Item
          name="password"
          label={t('password_label')}
          // §8.2: "password requirements appear before failure".
          extra={t('password_help')}
          rules={newPasswordRules(t)}
        >
          <Input.Password size="large" autoComplete="new-password" />
        </Form.Item>

        <Button type="primary" size="large" block htmlType="submit" loading={signUp.isPending}>
          {signUp.isPending ? t('sign_up_pending') : t('sign_up_submit')}
        </Button>
      </Form>
    </AuthCard>
  );
}
