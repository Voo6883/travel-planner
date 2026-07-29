package com.travelplanner.domain.enums;

/**
 * How densely the traveller wants their days filled.
 *
 * <p>Feeds {@code area_coverage} in the C2 fit score (PLAN §4.1.2) and the items-per-day budget C3
 * plans against, which is why it is an ordinal band rather than a number: nobody can answer "how
 * many activities per day" honestly, and a number invites a false precision the itinerary would
 * then be held to.
 */
public enum TravelPace {

    /** Few commitments per day, long stays in one area. */
    RELAXED,

    /** A balanced day — the default most travellers describe. */
    MODERATE,

    /** As much as fits, accepting the travel time between items. */
    PACKED
}
