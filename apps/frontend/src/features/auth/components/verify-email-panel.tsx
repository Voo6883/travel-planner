'use client';

import { Alert, Button, Form, Input } from 'antd';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { LoadingState } from '@/components/ui/loading-state';
import { AuthCard } from './auth-card';
import { AuthErrorNotice } from './auth-error-notice';
import { useResendVerification } from '../hooks/use-account-mail';
import { useVerifyEmail } from '../hooks/use-verify-email';
import { emailRules } from '../lib/form-rules';
import { emailOnlySchema } from '../schemas/credentials.schema';
import type { EmailFormValues } from '../types';

/**
 * Confirm an address from the mailed link (UC-A08), and resend one (UC-A13).
 *
 * One page for both because they are the same moment in the user's head: they arrived from a mail
 * that either worked or did not. Reaching `/verify-email` without a token is therefore not an
 * error — it is the resend form, which is where the sign-in page's `email_not_verified` state
 * links to.
 *
 * Verification runs on mount rather than behind a button; clicking the link *was* the intent, and
 * `useVerifyEmail` guards the single-use token against React's double effect invocation.
 */
export function VerifyEmailPanel() {
  const t = useTranslations('auth');
  const token = useSearchParams().get('token');
  const verify = useVerifyEmail({ token });

  if (token === null || token === '') {
    return <ResendVerificationCard />;
  }

  if (verify.isPending || verify.isIdle) {
    return (
      <AuthCard title={t('verify_email_title')}>
        <LoadingState label={t('verify_email_pending')} rows={2} />
      </AuthCard>
    );
  }

  if (verify.isSuccess) {
    return (
      <AuthCard title={t('verify_email_success_title')} description={t('verify_email_success_body')}>
        <Alert type="success" showIcon role="status" message={t('verify_email_success_title')} />
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
    <AuthCard title={t('verify_email_failed_title')} description={t('verify_email_failed_body')}>
      <div className="mb-6">
        <AuthErrorNotice error={verify.error} />
      </div>
      <ResendVerificationForm />
    </AuthCard>
  );
}

function ResendVerificationCard() {
  const t = useTranslations('auth');

  return (
    <AuthCard
      title={t('resend_verification_title')}
      footer={
        <Link href="/sign-in" className="text-action-primary-text">
          {t('go_to_sign_in')}
        </Link>
      }
    >
      <ResendVerificationForm />
    </AuthCard>
  );
}

/**
 * Same constant `202` as forgot-password, for the same reason: the response is identical whether
 * the address is registered, unregistered, already verified, or provider-only (ADR 009 §6).
 */
function ResendVerificationForm() {
  const t = useTranslations('auth');
  const [form] = Form.useForm<EmailFormValues>();
  const resend = useResendVerification();

  if (resend.isSuccess) {
    return <Alert type="success" showIcon role="status" message={t('resend_verification_sent')} />;
  }

  return (
    <>
      {resend.error ? (
        <div className="mb-4">
          <AuthErrorNotice error={resend.error} />
        </div>
      ) : null}

      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        disabled={resend.isPending}
        onFinish={(values: EmailFormValues) => resend.mutate(emailOnlySchema.parse(values))}
      >
        <Form.Item name="email" label={t('email_label')} rules={emailRules(t)}>
          <Input
            size="large"
            autoComplete="email"
            inputMode="email"
            placeholder={t('email_placeholder')}
          />
        </Form.Item>

        <Button type="primary" size="large" block htmlType="submit" loading={resend.isPending}>
          {t('resend_verification_submit')}
        </Button>
      </Form>
    </>
  );
}
