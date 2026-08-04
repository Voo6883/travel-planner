package com.travelplanner.domain.enums;

/**
 * Whether a plan may be shown to the traveller as their plan (task 28 DoD: "invalid schedules
 * cannot be persisted as ready").
 *
 * <p>Two values, not four. The scheduler's richer outcomes — infeasible, partial, unknown data —
 * live on {@code SchedulingResult} and are answers to "what happened when I tried to build this",
 * which is a different question from "is there a plan here". Persisting those as statuses would
 * put a failure report in the column the UI reads to decide whether to render a timeline.
 */
public enum ItineraryStatus {

    /** Being assembled, or assembled and found wanting. Never published as a plan. */
    DRAFT,

    /**
     * Every day validated: no overlaps, every block inside its day window, no POI scheduled outside
     * opening hours that were known. {@code ck_itinerary_ready_has_days} is not a database
     * constraint — see {@code Itinerary.requireReadyIsPublishable}, which enforces it where the days
     * are actually in scope.
     */
    READY;

    /** UC-C3-01: only a {@code READY} plan unlocks the itinerary view. */
    public boolean isPublishable() {
        return this == READY;
    }
}
