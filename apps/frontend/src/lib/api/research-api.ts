import type { components } from '@/generated/api/schema';
import { apiRequest } from './client';
import { researchJobSchema } from './schemas/research.schema';

/**
 * C2 research-job API surface (UC-C2-01/02).
 *
 * React-free and typed only by the generated contract, like every other `lib/api/<resource>-api.ts`.
 * Feature hooks own polling and cache invalidation; this stays a typed transport boundary.
 */

export type ResearchJob = components['schemas']['ResearchJob'];
export type ResearchJobStatus = components['schemas']['ResearchJobStatus'];

export interface StartResearchCommand {
  tripId: string;
}

export interface ResearchJobCommand {
  tripId: string;
  jobId: string;
}

/** UC-C2-01. Returns the queued job (HTTP 202); the run continues on a background worker. */
export async function startResearch(command: StartResearchCommand): Promise<ResearchJob> {
  return apiRequest({
    path: `/trips/${command.tripId}/research/run` as '/trips/{tripId}/research/run',
    method: 'POST',
    validate: (payload) => researchJobSchema.parse(payload),
  });
}

/** UC-C2-02. Polled while the job is queued or running. */
export async function fetchResearchJob(
  command: ResearchJobCommand,
  signal?: AbortSignal,
): Promise<ResearchJob> {
  return apiRequest({
    path:
      `/trips/${command.tripId}/research/jobs/${command.jobId}` as '/trips/{tripId}/research/jobs/{jobId}',
    signal,
    validate: (payload) => researchJobSchema.parse(payload),
  });
}
