import type { Rule } from 'antd/es/form';
import {
  EMAIL_MAX_LENGTH,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  USERNAME_MAX_LENGTH,
  USERNAME_MIN_LENGTH,
  USERNAME_PATTERN,
} from '../schemas/credentials.schema';

/** Translator handed in, so these stay pure functions and every message is localised. */
type Translate = (key: string) => string;

/**
 * Ant Design validation rules, built from the same constants as the zod schemas.
 *
 * Two layers on purpose (PLAN §4.2.6-F): Ant rules give per-field feedback as the user types,
 * zod checks the shape once before the request. Both read `credentials.schema.ts`, so a bound can
 * only be changed in one place.
 *
 * Messages are translated rather than passed through from Ant's English defaults — §11.3 requires
 * every visible string, validation errors included, to come from next-intl.
 */
export function emailRules(t: Translate): Rule[] {
  return [
    { required: true, message: t('validation_email_required') },
    { type: 'email', message: t('validation_email_invalid') },
    { max: EMAIL_MAX_LENGTH, message: t('validation_email_invalid') },
  ];
}

export function loginRules(t: Translate): Rule[] {
  return [
    { required: true, message: t('validation_login_required') },
    { max: EMAIL_MAX_LENGTH, message: t('validation_login_required') },
  ];
}

export function usernameRules(t: Translate): Rule[] {
  return [
    { required: true, message: t('validation_username_required') },
    {
      min: USERNAME_MIN_LENGTH,
      max: USERNAME_MAX_LENGTH,
      message: t('validation_username_length'),
    },
    { pattern: USERNAME_PATTERN, message: t('validation_username_pattern') },
  ];
}

/** For sign-in, where any non-empty value is worth submitting — the server decides. */
export function currentPasswordRules(t: Translate): Rule[] {
  return [{ required: true, message: t('validation_password_required') }];
}

/** For sign-up and reset, where the policy is known up front and should be shown before failure. */
export function newPasswordRules(t: Translate): Rule[] {
  return [
    { required: true, message: t('validation_password_required') },
    {
      min: PASSWORD_MIN_LENGTH,
      max: PASSWORD_MAX_LENGTH,
      message: t('validation_password_length'),
    },
  ];
}

export function confirmPasswordRules(t: Translate, field: string): Rule[] {
  return [
    { required: true, message: t('validation_password_required') },
    ({ getFieldValue }) => ({
      validator(_, value: string) {
        if (!value || getFieldValue(field) === value) {
          return Promise.resolve();
        }
        return Promise.reject(new Error(t('validation_password_mismatch')));
      },
    }),
  ];
}
