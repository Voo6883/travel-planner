package com.travelplanner.domain.enums;

/**
 * Wizard progress for a trip — the exact vocabulary of the "Trip status" table in
 * {@code plans/USE-CASES.md}, in the order that table lists it.
 *
 * <p>The names are the persisted values ({@code trip.status varchar} with a CHECK constraint) and
 * the wire values. They are therefore a contract in three places at once, which is why
 * {@code TripStatusTest} asserts the set rather than trusting review to catch a rename.
 *
 * <p>No transition logic lives here. Which moves are legal is
 * {@code tasks/18-trip-brief-core.md}'s decision for {@code DRAFT}/{@code CLARIFICATION_NEEDED}/
 * {@code BRIEF_COMPLETE}, and later tasks own the rest; encoding a guess now would lock a table
 * nobody reviewed.
 */
public enum TripStatus {

    /** Trip created, brief incomplete. The user can edit the brief (C1). */
    DRAFT,

    /** A valid {@code TripBrief} is saved. Research (C2) may start. */
    BRIEF_COMPLETE,

    /** Brief incomplete — typed questions were returned. The user answers them (C1). */
    CLARIFICATION_NEEDED,

    /** Research job submitted. */
    RESEARCH_QUEUED,

    /** Research agent in progress. */
    RESEARCH_RUNNING,

    /** Ranked recommendations available for selection (C2). */
    RESEARCH_READY,

    /** The user picked a recommendation. Itinerary generation (C3) may start. */
    DESTINATION_SELECTED,

    /** A day-by-day plan exists. Refine (C5) or book (C4). */
    ITINERARY_READY,

    /** Quotes held, confirmation pending (C4). */
    BOOKING_IN_PROGRESS,

    /** At least one confirmed booking. */
    BOOKED,

    /** User archived the trip. View only. */
    ARCHIVED;

    /** Archived trips are read-only for every actor, the agent included. */
    public boolean isReadOnly() {
        return this == ARCHIVED;
    }
}
