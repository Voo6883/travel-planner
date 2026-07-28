'use client';

import { Button, Form, Input } from 'antd';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { ApiError, isErrorCode } from '@/lib/api/api-error';
import { AuthCard } from './auth-card';
import { AuthErrorNotice } from './auth-error-notice';
import { AuthProviderButtons } from './auth-provider-buttons';
import { useSignIn } from '../hooks/use-sign-in';
import { currentPasswordRules, loginRules } from '../lib/form-rules';
import { signInSchema } from '../schemas/credentials.schema';
import type { SignInFormValues } from '../types';

/**
 * Sign-in screen (UC-A04, plus UC-A05/UC-A06 through the provider buttons).
 *
 * The identifier field is one input for email *and* username, matching the contract: the server
 * decides which by looking for `@`. Two fields, or a client-side guess, would produce "invalid
 * credentials" for a correct password whenever the guess disagreed with the server.
 *
 * `?error=<code>` is read on mount because a failed GitHub round trip comes back as a browser
 * redirect, not as a response this page can await — the registered code in the query string is
 * how the redirect hands the failure over, and it renders through the same translated path a JSON
 * envelope would.
 */
export function SignInPanel() {
  const t = useTranslations('auth');
  const [form] = Form.useForm<SignInFormValues>();
  const signIn = useSignIn();
  const redirectError = useRedirectError();
  const [dirtySinceError, setDirtySinceError] = useState(false);

  const failure = dirtySinceError ? null : (signIn.error ?? redirectError);

  const handleFinish = (values: SignInFormValues) => {
    setDirtySinceError(false);
    signIn.mutate(signInSchema.parse(values));
  };

  return (
    <AuthCard
      title={t('sign_in_title')}
      description={t('sign_in_subtitle')}
      footer={
        <span className="text-foreground-muted">
          {t('no_account_question')}{' '}
          <Link href="/sign-up" className="text-action-primary-text">
            {t('go_to_sign_up')}
          </Link>
        </span>
      }
    >
      <AuthProviderButtons mode="sign_in" />

      {failure ? (
        <div className="mb-4">
          <AuthErrorNotice error={failure} />
        </div>
      ) : null}

      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        disabled={signIn.isPending}
        onValuesChange={() => setDirtySinceError(true)}
        onFinish={handleFinish}
      >
        <Form.Item name="login" label={t('login_label')} rules={loginRules(t)}>
          <Input
            size="large"
            autoComplete="username"
            inputMode="email"
            placeholder={t('login_placeholder')}
          />
        </Form.Item>

        <Form.Item name="password" label={t('password_label')} rules={currentPasswordRules(t)}>
          <Input.Password size="large" autoComplete="current-password" />
        </Form.Item>

        <Button type="primary" size="large" block htmlType="submit" loading={signIn.isPending}>
          {signIn.isPending ? t('sign_in_pending') : t('sign_in_submit')}
        </Button>
      </Form>

      <p className="mb-0 mt-2 text-center">
        {/* Standalone link, so §6.1 requires a 44 px hit area rather than the 19 px of its text. */}
        <Link
          href="/forgot-password"
          className="inline-flex min-h-control items-center justify-center px-2 text-body-sm text-action-primary-text"
        >
          {t('forgot_password_link')}
        </Link>
      </p>
    </AuthCard>
  );
}

/**
 * Rebuilds an `ApiError` from `?error=<code>`, so a redirect failure and a fetch failure render
 * through exactly one path.
 *
 * Unregistered values are ignored rather than displayed: the query string is attacker-controllable,
 * and rendering whatever it contains would let a crafted link put arbitrary text on the sign-in
 * page. `isErrorCode` restricts it to codes the contract publishes.
 */
function useRedirectError(): ApiError | null {
  const searchParams = useSearchParams();
  const code = searchParams.get('error');

  if (code === null || !isErrorCode(code)) {
    return null;
  }
  return new ApiError({ status: 400, code, message: 'Sign-in failed.' });
}
