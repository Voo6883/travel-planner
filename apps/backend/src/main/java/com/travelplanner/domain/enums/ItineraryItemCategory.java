package com.travelplanner.domain.enums;

/**
 * What a scheduled block is (TRAVEL-KNOWLEDGE-CATALOG {@code itinerary_item.item_type},
 * UC-C3-06/08).
 *
 * <p>The vocabulary is fixed by {@code ck_itinerary_item_category}. It is deliberately short: the
 * timeline renders a different affordance per category, and a category nothing renders differently
 * is a column value pretending to be a feature.
 */
public enum ItineraryItemCategory {

    /** A place worth going to. Grounded in a {@code poi} row (UC-C3-03). */
    SIGHT,

    /**
     * A meal (UC-C3-06). Distinct from {@code SIGHT} even though both come from {@code poi},
     * because the scheduler reserves meal slots by clock time rather than by interest match — a day
     * with no lunch is a scheduling defect, not a preference.
     */
    FOOD,

    /**
     * Time spent moving, as a block on the timeline rather than as a leg between two.
     *
     * <p>Distinct from task 29's {@code itinerary_leg}: a leg annotates the gap <em>between</em>
     * two items with a mode and a duration, while this is a block that occupies the day in its own
     * right — an airport transfer, a day trip's outbound train. The scheduler emits these only when
     * given them as input; it never invents travel time.
     */
    TRANSIT,

    /**
     * Deliberately unscheduled time.
     *
     * <p>A real plan element, not padding. A day packed wall to wall is the most common way an
     * itinerary becomes unusable, so pace limits express themselves as free time rather than as a
     * shorter day.
     */
    FREE_TIME
}
