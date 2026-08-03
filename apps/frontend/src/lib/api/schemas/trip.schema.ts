import { z } from 'zod';
import type { components } from '@/generated/api/schema';

/**
 * Runtime validation for C1 trip intake endpoints.
 *
 * The DTO shapes are still the generated OpenAPI contract. These schemas add the runtime check
 * TypeScript cannot provide: that the server bytes match the contract before the form decides what
 * can be edited, saved, or treated as read-only.
 */

const tripStatusSchema: z.ZodType<components['schemas']['TripStatus']> = z.union([
  z.literal('DRAFT'),
  z.literal('BRIEF_COMPLETE'),
  z.literal('CLARIFICATION_NEEDED'),
  z.literal('RESEARCH_QUEUED'),
  z.literal('RESEARCH_RUNNING'),
  z.literal('RESEARCH_READY'),
  z.literal('DESTINATION_SELECTED'),
  z.literal('ITINERARY_READY'),
  z.literal('BOOKING_IN_PROGRESS'),
  z.literal('BOOKED'),
  z.literal('ARCHIVED'),
]);

const dateFlexibilitySchema: z.ZodType<components['schemas']['DateFlexibility']> = z.union([
  z.literal('FIXED'),
  z.literal('FLEXIBLE_WEEK'),
  z.literal('FLEXIBLE_MONTH'),
]);

const travelPaceSchema: z.ZodType<components['schemas']['TravelPace']> = z.union([
  z.literal('RELAXED'),
  z.literal('MODERATE'),
  z.literal('PACKED'),
]);

const travelInterestSchema: z.ZodType<components['schemas']['TravelInterest']> = z.union([
  z.literal('FOOD'),
  z.literal('SIGHTSEEING'),
  z.literal('MUSEUMS'),
  z.literal('NATURE'),
  z.literal('SHOPPING'),
  z.literal('NIGHTLIFE'),
  z.literal('EXPERIENCES'),
]);

const clarificationTypeSchema: z.ZodType<components['schemas']['ClarificationType']> = z.union([
  z.literal('TEXT'),
  z.literal('NUMBER'),
  z.literal('MONEY'),
  z.literal('DATE_RANGE'),
  z.literal('CHOICE'),
  z.literal('MULTI_CHOICE'),
]);

export const moneySchema: z.ZodType<components['schemas']['Money']> = z.object({
  amount: z.string(),
  currency: z.string(),
});

export const dateRangeSchema: z.ZodType<components['schemas']['DateRange']> = z.object({
  start_date: z.string(),
  end_date: z.string(),
});

export const partySizeSchema: z.ZodType<components['schemas']['PartySize']> = z.object({
  adults: z.number().int(),
  children: z.number().int(),
});

export const tripSchema: z.ZodType<components['schemas']['Trip']> = z.object({
  trip_id: z.string().uuid(),
  name: z.string(),
  status: tripStatusSchema,
  selected_recommendation_id: z.string().uuid().nullish(),
  version: z.number().int(),
  created_at: z.string(),
  updated_at: z.string(),
});

export const tripListSchema: z.ZodType<components['schemas']['TripList']> = z.object({
  trips: z.array(tripSchema),
});

export const clarificationQuestionSchema: z.ZodType<components['schemas']['ClarificationQuestion']> = z.object({
  id: z.string(),
  prompt_key: z.string(),
  type: clarificationTypeSchema,
  options: z.array(z.string()),
  required: z.boolean(),
});

export const clarificationSchema: z.ZodType<components['schemas']['Clarification']> = z.object({
  questions: z.array(clarificationQuestionSchema),
});

export const tripBriefSchema: z.ZodType<components['schemas']['TripBrief']> = z.object({
  trip_id: z.string().uuid(),
  status: tripStatusSchema,
  destinations: z.array(z.string()),
  surprise_me: z.boolean(),
  dates: dateRangeSchema.nullish(),
  date_flexibility: dateFlexibilitySchema.nullish(),
  departure_city: z.string().nullish(),
  budget: moneySchema.nullish(),
  party: partySizeSchema.nullish(),
  interests: z.array(travelInterestSchema),
  pace: travelPaceSchema.nullish(),
  clarification: clarificationSchema,
  version: z.number().int(),
  created_at: z.string(),
  updated_at: z.string(),
});
