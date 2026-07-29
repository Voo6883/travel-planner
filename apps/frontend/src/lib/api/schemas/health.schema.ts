import { z } from 'zod';
import type { components } from '@/generated/api/schema';

/**
 * Runtime validation for the platform endpoints (PLAN §6.5).
 *
 * These are not duplicated DTOs — the shape still comes from the contract. Each schema is
 * annotated with its generated type, so a contract change that this file does not follow is a
 * compile error, not a silent divergence. What the schema adds is the guarantee types cannot
 * give: that the bytes actually on the wire match the promise.
 */

export const healthResponseSchema: z.ZodType<components['schemas']['HealthResponse']> = z.object({
  status: z.literal('UP'),
});

export const readinessResponseSchema: z.ZodType<components['schemas']['ReadinessResponse']> = z.object({
  status: z.union([z.literal('UP'), z.literal('DOWN')]),
  components: z.record(z.string()),
});
