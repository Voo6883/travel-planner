import { z } from 'zod';
import type { components } from '@/generated/api/schema';

/**
 * Runtime validation for the admin endpoints (PLAN §6.5, §4.2.6-G).
 *
 * Not duplicated DTOs — the shape still comes from the contract, and each schema is annotated with
 * its generated type, so a contract change this file does not follow is a compile error rather
 * than a silent divergence. What the schema adds is the guarantee a type cannot: that the bytes on
 * the wire match the promise.
 *
 * It earns its place here for the same reason it does on `/auth/me`. `enabled` and `closed` drive
 * which destructive action a screen offers, and `has_local_password` decides whether "reset
 * password" is shown at all. A field silently absent from the payload would parse as `undefined`
 * and render the wrong affordance for somebody else's account.
 */

const roleSchema = z.union([z.literal('USER'), z.literal('ADMIN')]);

const providerSchema = z.union([
  z.literal('LOCAL'),
  z.literal('FIREBASE_GOOGLE'),
  z.literal('GITHUB'),
]);

/**
 * `email` is a plain string, deliberately. A closed account carries the unroutable
 * `deleted-<id>@deleted.invalid` placeholder UC-A14 leaves behind, and `z.string().email()` would
 * reject the very row the list has to be able to show.
 */
export const adminUserSummarySchema: z.ZodType<components['schemas']['AdminUserSummary']> = z.object(
  {
    user_id: z.string().uuid(),
    email: z.string(),
    username: z.string().nullish(),
    roles: z.array(roleSchema),
    email_verified: z.boolean(),
    enabled: z.boolean(),
    closed: z.boolean(),
    created_at: z.string(),
  },
);

export const adminUserDetailSchema: z.ZodType<components['schemas']['AdminUserDetail']> = z.object({
  user_id: z.string().uuid(),
  email: z.string(),
  username: z.string().nullish(),
  roles: z.array(roleSchema),
  email_verified: z.boolean(),
  enabled: z.boolean(),
  closed: z.boolean(),
  created_at: z.string(),
  linked_providers: z.array(providerSchema),
  has_local_password: z.boolean(),
  updated_at: z.string(),
});

export const adminUserPageSchema: z.ZodType<components['schemas']['AdminUserPage']> = z.object({
  page: z.number().int(),
  page_size: z.number().int(),
  total: z.number().int(),
  items: z.array(adminUserSummarySchema),
});
