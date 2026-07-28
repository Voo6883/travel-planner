'use client';

import { Alert, Form, Input, Modal } from 'antd';
import { useTranslations } from 'next-intl';
import { ErrorAlert } from '@/components/ui/error-alert';
import type { AdminUserDetail } from '@/lib/api/admin-api';
import { useResetAdminUserPassword } from '../hooks/use-admin-users';

interface ResetPasswordFormValues {
  new_password: string;
  confirm_password: string;
}

export interface ResetPasswordModalProps {
  account: AdminUserDetail;
  open: boolean;
  onClose: () => void;
}

/**
 * Set a temporary password on somebody else's account (UC-A16).
 *
 * §8.10 requires this to be a dedicated modal rather than an inline field, and the reason is worth
 * stating: it is the most destructive thing an administrator can do to an account that is still
 * working, and it must not be one stray click away from the enable/disable control beside it.
 *
 * The confirmation names the account (§6.6 "destructive confirmation names the affected item") and
 * says plainly that every session ends (ADR 009 §1). It also says the value is shown once and
 * never again, because the administrator has to pass it on out of band and there is no endpoint
 * that reads it back — discovering that afterwards is discovering it too late.
 *
 * All three §6.8 states are present: the OK button carries the pending state and locks the form,
 * a failure renders the shared `ErrorAlert`, and success replaces the form with a confirmation
 * rather than silently closing — a modal that vanishes leaves the administrator unsure whether the
 * password they typed is now live on somebody's account.
 */
export function ResetPasswordModal({ account, open, onClose }: ResetPasswordModalProps) {
  const t = useTranslations('admin');
  const [form] = Form.useForm<ResetPasswordFormValues>();
  const reset = useResetAdminUserPassword();

  return (
    <Modal
      open={open}
      title={t('reset.title', { email: account.email })}
      okText={reset.isSuccess ? t('reset.done_action') : t('reset.submit')}
      okButtonProps={{ danger: !reset.isSuccess, loading: reset.isPending }}
      cancelButtonProps={{ style: reset.isSuccess ? { display: 'none' } : undefined }}
      onOk={() => (reset.isSuccess ? close() : void form.submit())}
      onCancel={close}
      // Nothing typed into this form may outlive the dialog: the field holds a working credential
      // for somebody else's account, and a form Ant kept mounted would keep it in memory — and in
      // the DOM — until the page navigated away.
      destroyOnHidden
    >
      {reset.isSuccess ? (
        <Alert
          type="success"
          showIcon
          role="status"
          message={t('reset.success_title')}
          description={t('reset.success_body', { email: account.email })}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <p className="m-0 text-body-sm">{t('reset.body', { email: account.email })}</p>
          <Alert type="warning" showIcon message={t('reset.warning')} />

          {reset.error ? <ErrorAlert error={reset.error} /> : null}

          <Form
            form={form}
            layout="vertical"
            requiredMark={false}
            disabled={reset.isPending}
            onFinish={(values: ResetPasswordFormValues) =>
              reset.mutate({ userId: account.user_id, newPassword: values.new_password })
            }
          >
            <Form.Item
              name="new_password"
              label={t('reset.new_password_label')}
              extra={t('reset.new_password_help')}
              rules={[
                { required: true, message: t('reset.validation_required') },
                { min: 8, max: 72, message: t('reset.validation_length') },
              ]}
            >
              <Input.Password size="large" autoComplete="new-password" />
            </Form.Item>

            <Form.Item
              name="confirm_password"
              label={t('reset.confirm_password_label')}
              dependencies={['new_password']}
              rules={[
                { required: true, message: t('reset.validation_required') },
                ({ getFieldValue }) => ({
                  validator: (_, value: string) =>
                    !value || getFieldValue('new_password') === value
                      ? Promise.resolve()
                      : Promise.reject(new Error(t('reset.validation_mismatch'))),
                }),
              ]}
            >
              <Input.Password size="large" autoComplete="new-password" />
            </Form.Item>
          </Form>
        </div>
      )}
    </Modal>
  );

  function close() {
    reset.reset();
    form.resetFields();
    onClose();
  }
}
