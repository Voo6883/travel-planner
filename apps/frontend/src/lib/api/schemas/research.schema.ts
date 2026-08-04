import { z } from 'zod';
import type { components } from '@/generated/api/schema';
import { moneySchema } from './trip.schema';

/**
 * Runtime validation for the C2 research endpoints (UC-C2-01/02/03/05/06).
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

const sourceRefSchema: z.ZodType<components['schemas']['RecommendationSourceRef']> = z.object({
  source_ref: z.string(),
  source_url: z.string().nullish(),
  field_group: z.string(),
});

/**
 * A citation read live from the knowledge base, as opposed to one replayed from the JSON frozen
 * into a `ranked_recommendation` row.
 *
 * The extra fields are the ones a reader needs in order to weigh the claim: `sample_data` says the
 * row backs no real-world fact (ADR 010 §3), `stale` says it has outlived its TTL (§6), and
 * `attribution` is the licence obligation the UI has to discharge where the text is shown (§2).
 * A recommendation's frozen refs cannot answer "is this stale *now*", so they do not carry these.
 */
const knowledgeSourceRefSchema: z.ZodType<components['schemas']['KnowledgeSourceRef']> = z.object({
  source_ref: z.string(),
  source_url: z.string().nullish(),
  field_group: z.string(),
  attribution: z.string().nullish(),
  sample_data: z.boolean(),
  stale: z.boolean(),
  retrieved_at: z.string(),
});

const localAppPackEntrySchema: z.ZodType<components['schemas']['LocalAppPackEntry']> = z.object({
  usage: z.string(),
  name: z.string(),
  slug: z.string().nullish(),
});

const scoreBreakdownSchema: z.ZodType<components['schemas']['ScoreBreakdown']> = z.object({
  interest_match: z.number(),
  seasonality_fit: z.number(),
  price_fit: z.number(),
  area_coverage: z.number(),
  freshness_factor: z.number(),
  confidence: z.number(),
  fit_score: z.number(),
});

const travelerGuideSchema: z.ZodType<components['schemas']['TravelerGuide']> = z.object({
  overview: z.string(),
  why_now: z.string().nullish(),
  areas: z.array(z.string()),
  food: z.string().nullish(),
  highlights: z.array(z.string()),
  mobility: z.string().nullish(),
  practical: z.string().nullish(),
  local_app_pack: z.array(localAppPackEntrySchema),
  source_refs: z.array(sourceRefSchema),
});

export const rankedRecommendationSchema: z.ZodType<components['schemas']['RankedRecommendation']> = z.object({
  recommendation_id: z.string().uuid(),
  destination_id: z.string().uuid(),
  destination_slug: z.string(),
  country_code: z.string(),
  rank: z.number().int(),
  fit_score: z.number(),
  score_breakdown: scoreBreakdownSchema,
  est_cost: moneySchema.nullish(),
  rationale: z.string(),
  traveler_guide: travelerGuideSchema,
  risks: z.array(z.string()),
  best_window: z.string().nullish(),
  source_refs: z.array(sourceRefSchema),
  algorithm_version: z.string(),
});

export const rankedRecommendationsSchema: z.ZodType<components['schemas']['RankedRecommendations']> = z.object({
  trip_id: z.string().uuid(),
  research_run_id: z.string().uuid(),
  no_confident_result: z.boolean(),
  algorithm_version: z.string(),
  selected_recommendation_id: z.string().uuid().nullish(),
  recommendations: z.array(rankedRecommendationSchema),
});

export const destinationGuideDetailSchema: z.ZodType<components['schemas']['DestinationGuideDetail']> = z.object({
  destination_id: z.string().uuid(),
  slug: z.string(),
  name: z.string(),
  country_code: z.string(),
  locale: z.string(),
  overview: z.string().nullish(),
  food: z.string().nullish(),
  practical: z.string().nullish(),
  areas: z.array(
    z.object({
      slug: z.string(),
      name: z.string(),
      description: z.string().nullish(),
    }),
  ),
  top_pois: z.array(
    z.object({
      poi_id: z.string().uuid(),
      slug: z.string(),
      name: z.string(),
      category: z.string(),
      description: z.string().nullish(),
    }),
  ),
  transport_modes: z.array(
    z.object({
      mode: z.string(),
      display_name: z.string(),
      description: z.string().nullish(),
    }),
  ),
  local_app_pack: z.array(
    z.object({
      slug: z.string(),
      name: z.string(),
      category: z.string(),
      description: z.string().nullish(),
    }),
  ),
  source_refs: z.array(knowledgeSourceRefSchema),
  sample_data: z.boolean(),
});
