import { z } from 'zod';
import type { components } from '@/generated/api/schema';

/**
 * Runtime validation for the account endpoints (PLAN §6.5, §4.2.6-G).
 *
 * These are not duplicated DTOs — the shape still comes from the contract. Each schema is
 * annotated with its generated type, so a contract change this file does not follow is a compile
 * error rather than a silent divergence. What the schema adds is the guarantee types cannot give:
 * that the bytes actually on the wire match the promise.
 *
 * This matters more here than anywhere else in the app. Everything downstream of `/auth/me` —
 * the planner guard, the admin guard, the verification gate — is an authorisation decision made
 * from this payload, and an unparsed `roles` or a missing `email_verified` would fail *open*.
 */

const providerSchema = z.union([
  z.literal('LOCAL'),
  z.literal('FIREBASE_GOOGLE'),
  z.literal('GITHUB'),
]);

export const currentUserSchema: z.ZodType<components['schemas']['CurrentUser']> = z.object({
  user_id: z.string().uuid(),
  email: z.string(),
  username: z.string().nullish(),
  roles: z.array(z.union([z.literal('USER'), z.literal('ADMIN')])),
  email_verified: z.boolean(),
  linked_providers: z.array(providerSchema),
});

export const authSessionResponseSchema: z.ZodType<components['schemas']['AuthSessionResponse']> =
  z.object({
    user: currentUserSchema,
    is_new_user: z.boolean(),
    provider_linked: z.boolean(),
  });

/**
 * Constant by design. The value carries no information about whether the account existed — that
 * is ADR 009 §6's enumeration defence, and parsing it strictly is what stops a future change from
 * quietly reintroducing a distinguishable response.
 */
export const registrationResponseSchema: z.ZodType<components['schemas']['RegistrationResponse']> =
  z.object({
    status: z.literal('PENDING_VERIFICATION'),
  });

export const acceptedResponseSchema: z.ZodType<components['schemas']['AcceptedResponse']> = z.object(
  {
    status: z.literal('ACCEPTED'),
  },
);
