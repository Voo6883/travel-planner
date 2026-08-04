import { z } from 'zod';
import type { components } from '@/generated/api/schema';

/**
 * Runtime validation for the C2 research-job endpoints (UC-C2-01/02).
 *
 * The DTO shapes stay the generated OpenAPI contract; this adds the check TypeScript cannot: that
 * the server bytes match before the poll UI decides whether the job is still running.
 */

const researchJobStatusSchema: z.ZodType<components['schemas']['ResearchJobStatus']> = z.union([
  z.literal('queued'),
  z.literal('running'),
  z.literal('completed'),
  z.literal('failed'),
]);

export const researchJobSchema: z.ZodType<components['schemas']['ResearchJob']> = z.object({
  job_id: z.string().uuid(),
  trip_id: z.string().uuid(),
  status: researchJobStatusSchema,
  progress_pct: z.number().int(),
  error_code: z.string().nullish(),
  attempts: z.number().int(),
  started_at: z.string().nullish(),
  completed_at: z.string().nullish(),
});
