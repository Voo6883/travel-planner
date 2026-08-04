import type { components } from '@/generated/api/schema';
import type { ApiPath } from './client';
import { apiRequest } from './client';
import {
  destinationGuideDetailSchema,
  rankedRecommendationsSchema,
  researchJobSchema,
} from './schemas/research.schema';
import { tripSchema } from './schemas/trip.schema';
import type { Trip } from './trip-api';

/**
 * C2 research API surface (UC-C2-01/02/03/05/06).
 *
 * React-free and typed only by the generated contract. Feature hooks own polling and cache
 * invalidation; this stays a typed transport boundary.
 */

export type ResearchJob = components['schemas']['ResearchJob'];
export type ResearchJobStatus = components['schemas']['ResearchJobStatus'];
export type RankedRecommendations = components['schemas']['RankedRecommendations'];
export type RankedRecommendation = components['schemas']['RankedRecommendation'];
export type DestinationGuideDetail = components['schemas']['DestinationGuideDetail'];

export interface StartResearchCommand {
  tripId: string;
}

export interface ResearchJobCommand {
  tripId: string;
  jobId: string;
}

export interface TripResearchCommand {
  tripId: string;
}

export interface SelectRecommendationCommand {
  tripId: string;
  recommendationId: string;
}

export interface DestinationGuideCommand {
  destinationId: string;
  locale?: 'en' | 'ms';
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
export async function fetchResearchJob(command: ResearchJobCommand, signal?: AbortSignal): Promise<ResearchJob> {
  return apiRequest({
    path: `/trips/${command.tripId}/research/jobs/${command.jobId}` as '/trips/{tripId}/research/jobs/{jobId}',
    signal,
    validate: (payload) => researchJobSchema.parse(payload),
  });
}

/** UC-C2-03/05. Latest durable research outcome when the trip is ready. */
export async function fetchRankedRecommendations(
  command: TripResearchCommand,
  signal?: AbortSignal,
): Promise<RankedRecommendations> {
  return apiRequest({
    path: `/trips/${command.tripId}/ranked-recommendations` as '/trips/{tripId}/ranked-recommendations',
    signal,
    validate: (payload) => rankedRecommendationsSchema.parse(payload),
  });
}

/** UC-C2-06. Waits for server confirmation — no optimistic selection. */
export async function selectRecommendation(command: SelectRecommendationCommand): Promise<Trip> {
  return apiRequest({
    path: `/trips/${command.tripId}/selected-recommendation` as '/trips/{tripId}/selected-recommendation',
    method: 'POST',
    body: { recommendation_id: command.recommendationId },
    validate: (payload) => tripSchema.parse(payload),
  });
}

/** PLAN §4.1 destination guide detail for drawers. */
export async function fetchDestinationGuide(
  command: DestinationGuideCommand,
  signal?: AbortSignal,
): Promise<DestinationGuideDetail> {
  const search = new URLSearchParams({ locale: command.locale ?? 'en' });
  return apiRequest({
    path: guidePath(command.destinationId, search),
    signal,
    validate: (payload) => destinationGuideDetailSchema.parse(payload),
  });
}

function guidePath(destinationId: string, search: URLSearchParams): ApiPath {
  return `/destinations/${destinationId}/guide?${search.toString()}` as '/destinations/{destinationId}/guide';
}
