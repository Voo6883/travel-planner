import type { components } from '@/generated/api/schema';
import { apiRequest } from './client';
import { tripBriefSchema, tripListSchema, tripSchema } from './schemas/trip.schema';

/**
 * C1 trip and brief API surface.
 *
 * These functions stay React-free and expose only generated contract types. Feature hooks own cache
 * invalidation and UI components own form behaviour, which keeps this layer a typed transport
 * boundary rather than a second place where product state can drift.
 */

export type Trip = components['schemas']['Trip'];
export type TripList = components['schemas']['TripList'];
export type TripStatus = components['schemas']['TripStatus'];
export type TripBrief = components['schemas']['TripBrief'];
export type Clarification = components['schemas']['Clarification'];
export type ClarificationQuestion = components['schemas']['ClarificationQuestion'];
export type ClarificationAnswer = components['schemas']['ClarificationAnswer'];
export type ClarificationType = components['schemas']['ClarificationType'];
export type DateRange = components['schemas']['DateRange'];
export type DateFlexibility = components['schemas']['DateFlexibility'];
export type Money = components['schemas']['Money'];
export type PartySize = components['schemas']['PartySize'];
export type TravelInterest = components['schemas']['TravelInterest'];
export type TravelPace = components['schemas']['TravelPace'];
export type CreateTripRequest = components['schemas']['CreateTripRequest'];
export type RenameTripRequest = components['schemas']['RenameTripRequest'];
export type ArchiveTripRequest = components['schemas']['ArchiveTripRequest'];
export type UpdateTripBriefRequest = components['schemas']['UpdateTripBriefRequest'];
export type AnswerClarificationRequest = components['schemas']['AnswerClarificationRequest'];

export interface TripIdCommand {
  tripId: string;
}

export interface RenameTripCommand extends TripIdCommand {
  request: RenameTripRequest;
}

export interface ArchiveTripCommand extends TripIdCommand {
  request: ArchiveTripRequest;
}

export interface UpdateTripBriefCommand extends TripIdCommand {
  request: UpdateTripBriefRequest;
}

export interface AnswerTripBriefClarificationCommand extends TripIdCommand {
  request: AnswerClarificationRequest;
}

/** UC-T02. Trips are user-scoped by the session; no owner id is accepted or returned. */
export async function fetchTrips(signal?: AbortSignal): Promise<TripList> {
  return apiRequest({
    path: '/trips',
    signal,
    validate: (payload) => tripListSchema.parse(payload),
  });
}

/** UC-T01b. Manual creation remains a fallback until the chat-first entry lands in task 21. */
export async function createTrip(request: CreateTripRequest): Promise<Trip> {
  return apiRequest({
    path: '/trips',
    method: 'POST',
    body: request,
    validate: (payload) => tripSchema.parse(payload),
  });
}

export async function fetchTrip(command: TripIdCommand, signal?: AbortSignal): Promise<Trip> {
  return apiRequest({
    path: `/trips/${command.tripId}` as '/trips/{tripId}',
    signal,
    validate: (payload) => tripSchema.parse(payload),
  });
}

export async function renameTrip(command: RenameTripCommand): Promise<Trip> {
  return apiRequest({
    path: `/trips/${command.tripId}` as '/trips/{tripId}',
    method: 'PUT',
    body: command.request,
    validate: (payload) => tripSchema.parse(payload),
  });
}

export async function deleteTrip(command: TripIdCommand): Promise<void> {
  await apiRequest<void>({
    path: `/trips/${command.tripId}` as '/trips/{tripId}',
    method: 'DELETE',
  });
}

export async function archiveTrip(command: ArchiveTripCommand): Promise<Trip> {
  return apiRequest({
    path: `/trips/${command.tripId}/actions/archive` as '/trips/{tripId}/actions/archive',
    method: 'POST',
    body: command.request,
    validate: (payload) => tripSchema.parse(payload),
  });
}

export async function fetchTripBrief(command: TripIdCommand, signal?: AbortSignal): Promise<TripBrief> {
  return apiRequest({
    path: `/trips/${command.tripId}/brief` as '/trips/{tripId}/brief',
    signal,
    validate: (payload) => tripBriefSchema.parse(payload),
  });
}

export async function updateTripBrief(command: UpdateTripBriefCommand): Promise<TripBrief> {
  return apiRequest({
    path: `/trips/${command.tripId}/brief` as '/trips/{tripId}/brief',
    method: 'PUT',
    body: command.request,
    validate: (payload) => tripBriefSchema.parse(payload),
  });
}

export async function answerTripBriefClarification(command: AnswerTripBriefClarificationCommand): Promise<TripBrief> {
  return apiRequest({
    path: `/trips/${command.tripId}/brief/actions/answer-clarification` as '/trips/{tripId}/brief/actions/answer-clarification',
    method: 'POST',
    body: command.request,
    validate: (payload) => tripBriefSchema.parse(payload),
  });
}
