package com.travelplanner.domain.algorithm.scheduling;

import com.travelplanner.domain.model.ItineraryItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of scheduling one day: what was placed, and everything that was not.
 *
 * <p>{@link Outcome} is the typed answer task 28's Definition of Done asks for. It is derived here
 * rather than passed in, because a caller that can label its own result {@code FEASIBLE} will
 * eventually do so for a day that is not.
 */
public record DayPlan(
        int dayNumber,
        LocalDate date,
        UUID areaId,
        DayPlanRequest.DayWindow window,
        List<ItineraryItem> items,
        List<SchedulingNotice> notices) {

    public DayPlan {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(window, "window");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
    }

    /**
     * <strong>Derived, never asserted.</strong> The three states are a function of what is in the
     * two lists, so they cannot drift from it.
     *
     * <p><strong>A day of nothing but held slots is infeasible, not partial.</strong> Reserving an
     * unfilled meal slot keeps a gap in the corpus visible (UC-C3-06), but a day whose only content
     * is a lunch placeholder with no restaurant behind it is a blank page with a label on it. It has
     * to fail here, because {@code Itinerary} only refuses days that are literally empty and this one
     * is not — it would otherwise be published as part of a READY plan.
     */
    public Outcome outcome() {
        if (items.isEmpty() || !hasGroundedItem()) {
            return Outcome.INFEASIBLE;
        }
        return notices.isEmpty() ? Outcome.FEASIBLE : Outcome.PARTIAL;
    }

    /**
     * Whether any block on this day is grounded in a real place (UC-C3-03).
     *
     * <p>Free time and an unfilled meal slot carry no POI by design. A day made only of those is a
     * day the scheduler could not fill, however many rows it contains.
     */
    public boolean hasGroundedItem() {
        return items.stream().anyMatch(item -> item.poiId() != null);
    }

    /** True when any placed block rests on hours nobody curated (task 28 DoD). */
    public boolean hasUnknownData() {
        return notices.stream().anyMatch(SchedulingNotice::isUnknownData);
    }

    public int scheduledMinutes() {
        return items.stream().mapToInt(ItineraryItem::durationMinutes).sum();
    }

    /** How a day came out. */
    public enum Outcome {

        /** Everything offered was placed, with no caveats. */
        FEASIBLE,

        /**
         * A usable day, with something left out or something uncertain.
         *
         * <p>The common case on a PARTIAL corpus, and deliberately not a failure: the notices say
         * what the caveat is, and a day the traveller can walk beats a refusal.
         */
        PARTIAL,

        /**
         * Nothing could be placed.
         *
         * <p>Never persisted as part of a {@code READY} plan — {@code Itinerary} refuses an empty
         * day in a publishable itinerary, so this outcome cannot quietly become a blank page in
         * somebody's trip.
         */
        INFEASIBLE
    }
}
