import { z } from 'zod';

/**
 * Client-side shape checks for the credential forms (PLAN §4.2.6-F).
 *
 * Every bound here is copied from the OpenAPI constraint it mirrors, and none of them is a
 * security control — the server re-validates all of it. What they buy is a user who learns their
 * password is too short before a round trip, and §8.2's "password requirements appear before
 * failure".
 *
 * The `@` exclusion on usernames is the one rule that is not merely cosmetic: sign-in accepts an
 * email *or* a username on one field and disambiguates on `@`, so a username shaped like an email
 * address would make that field ambiguous (ADR 009 §4).
 */

export const EMAIL_MAX_LENGTH = 320;
export const USERNAME_MIN_LENGTH = 3;
export const USERNAME_MAX_LENGTH = 64;
export const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 72;

export const emailSchema = z.string().trim().min(1).max(EMAIL_MAX_LENGTH).email();

export const usernameSchema = z
  .string()
  .trim()
  .min(USERNAME_MIN_LENGTH)
  .max(USERNAME_MAX_LENGTH)
  .regex(USERNAME_PATTERN);

export const passwordSchema = z.string().min(PASSWORD_MIN_LENGTH).max(PASSWORD_MAX_LENGTH);

export const signInSchema = z.object({
  login: z.string().trim().min(1).max(EMAIL_MAX_LENGTH),
  password: z.string().min(1),
});

export const signUpSchema = z.object({
  email: emailSchema,
  username: usernameSchema,
  password: passwordSchema,
});

export const emailOnlySchema = z.object({ email: emailSchema });

export const newPasswordSchema = z
  .object({
    new_password: passwordSchema,
    confirm_password: z.string(),
  })
  .refine((values) => values.new_password === values.confirm_password, {
    path: ['confirm_password'],
  });
