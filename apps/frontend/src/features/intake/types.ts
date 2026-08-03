/**
 * Intake aliases are generated-contract aliases from `lib/api`, the only feature-accessible layer
 * allowed to import OpenAPI output. UI-only form drafts live next to their mappers so this file
 * never becomes a hand-written shadow of the contract.
 */
export type {
  AnswerClarificationRequest,
  Clarification,
  ClarificationAnswer,
  ClarificationQuestion,
  ClarificationType,
  DateFlexibility,
  DateRange,
  Money,
  PartySize,
  TravelInterest,
  TravelPace,
  Trip,
  TripBrief,
  TripList,
  TripStatus,
  UpdateTripBriefRequest,
} from '@/lib/api/trip-api';
