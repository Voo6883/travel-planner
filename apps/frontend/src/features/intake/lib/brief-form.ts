import type {
  DateFlexibility,
  DateRange,
  Money,
  PartySize,
  TravelInterest,
  TravelPace,
  TripBrief,
  UpdateTripBriefRequest,
} from '../types';

export const DATE_FLEXIBILITIES = [
  'FIXED',
  'FLEXIBLE_WEEK',
  'FLEXIBLE_MONTH',
] as const satisfies readonly DateFlexibility[];

export const TRAVEL_PACES = ['RELAXED', 'MODERATE', 'PACKED'] as const satisfies readonly TravelPace[];

export const TRAVEL_INTERESTS = [
  'FOOD',
  'SIGHTSEEING',
  'MUSEUMS',
  'NATURE',
  'SHOPPING',
  'NIGHTLIFE',
  'EXPERIENCES',
] as const satisfies readonly TravelInterest[];

export const CURRENCIES = ['MYR', 'USD', 'JPY', 'EUR', 'SGD', 'THB', 'CNY'] as const;

export type TripBriefField = keyof TripBriefFormValues;

export interface BudgetFormValue {
  amount: string;
  currency: string;
}

export interface PartyFormValue {
  adults: number | null;
  children: number | null;
}

export interface TripBriefFormValues {
  destinations: string[];
  dates: DateRange | null;
  date_flexibility: DateFlexibility | null;
  departure_city: string;
  budget: BudgetFormValue;
  party: PartyFormValue;
  interests: TravelInterest[];
  pace: TravelPace | null;
}

/**
 * Full-resource PUT means the mapper is deliberately explicit about every editable field. A new
 * brief field should fail review here rather than be omitted and accidentally cleared.
 */
export function briefToFormValues(brief: TripBrief): TripBriefFormValues {
  return {
    destinations: brief.destinations,
    dates: brief.dates ?? null,
    date_flexibility: brief.date_flexibility ?? null,
    departure_city: brief.departure_city ?? '',
    budget: moneyToFormValue(brief.budget ?? null),
    party: partyToFormValue(brief.party ?? null),
    interests: brief.interests,
    pace: brief.pace ?? null,
  };
}

export function buildUpdateTripBriefRequest(
  values: TripBriefFormValues,
  expectedVersion: number,
): UpdateTripBriefRequest {
  return {
    expected_version: expectedVersion,
    destinations: normalizeDestinations(values.destinations),
    dates: values.dates,
    date_flexibility: values.date_flexibility,
    departure_city: blankToNull(values.departure_city),
    budget: formValueToMoney(values.budget),
    party: formValueToParty(values.party),
    interests: values.interests,
    pace: values.pace,
  };
}

export function mergeBriefFormValues(command: MergeBriefFormCommand): TripBriefFormValues {
  const server = briefToFormValues(command.serverBrief);
  const merged = { ...server };

  for (const field of command.keepFields) {
    merged[field] = command.draft[field] as never;
  }

  if (command.focusedField) {
    merged[command.focusedField] = command.draft[command.focusedField] as never;
  }

  return merged;
}

export interface MergeBriefFormCommand {
  serverBrief: TripBrief;
  draft: TripBriefFormValues;
  keepFields: ReadonlySet<TripBriefField>;
  focusedField: TripBriefField | null;
}

function moneyToFormValue(money: Money | null): BudgetFormValue {
  return { amount: money?.amount ?? '', currency: money?.currency ?? 'MYR' };
}

function partyToFormValue(party: PartySize | null): PartyFormValue {
  return { adults: party?.adults ?? null, children: party?.children ?? null };
}

function formValueToMoney(value: BudgetFormValue): Money | null {
  const amount = value.amount.trim();
  return amount ? { amount, currency: value.currency } : null;
}

function formValueToParty(value: PartyFormValue): PartySize | null {
  if (value.adults === null) {
    return null;
  }
  return { adults: value.adults, children: value.children ?? 0 };
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function normalizeDestinations(destinations: readonly string[]): string[] {
  return [...new Set(destinations.map((slug) => slug.trim().toLowerCase()).filter(Boolean))];
}
