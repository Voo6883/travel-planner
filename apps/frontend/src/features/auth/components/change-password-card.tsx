'use client';

import { Alert, Button, Card, Form, Input } from 'antd';
import { useTranslations } from 'next-intl';
import { AuthErrorNotice } from './auth-error-notice';
import { useChangePassword } from '../hooks/use-account-management';
import { confirmPasswordRules, currentPasswordRules, newPasswordRules } from '../lib/form-rules';
import { newPasswordSchema } from '../schemas/credentials.schema';
import type { ChangePasswordFormValues } from '../types';

/**
 * Change your own password (UC-A12, §8.9).
 *
 * The current password is required, so a borrowed unlocked browser cannot silently take the
 * account over — and a mismatch is `401 invalid_credentials`, not a validation error.
 *
 * Success ends **every** session including this one (ADR 009 §1). That is the feature, not a side
 * effect: a user who changes their password because they think they were compromised is telling
 * the system to evict everyone. The copy says so before they submit, because being signed out
 * without warning reads as a bug.
 */
export function ChangePasswordCard() {
  const t = useTranslations('auth');
  const [form] = Form.useForm<ChangePasswordFormValues>();
  const changePassword = useChangePassword();

  return (
    <Card title={t('change_password_title')}>
      <p className="mb-4 mt-0 text-body-sm text-foreground-muted">{t('change_password_body')}</p>

      {changePassword.isSuccess ? (
        <Alert type="success" showIcon role="status" message={t('change_password_success')} />
      ) : null}

      {changePassword.error ? (
        <div className="mb-4">
          <AuthErrorNotice error={changePassword.error} />
        </div>
      ) : null}

      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        className="max-w-form"
        disabled={changePassword.isPending}
        onFinish={(values: ChangePasswordFormValues) => submit(values)}
      >
        <Form.Item
          name="current_password"
          label={t('current_password_label')}
          rules={currentPasswordRules(t)}
        >
          <Input.Password size="large" autoComplete="current-password" />
        </Form.Item>

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

        <Button type="primary" size="large" htmlType="submit" loading={changePassword.isPending}>
          {t('change_password_submit')}
        </Button>
      </Form>
    </Card>
  );

  function submit(values: ChangePasswordFormValues) {
    newPasswordSchema.parse(values);
    changePassword.mutate(values);
  }
}
