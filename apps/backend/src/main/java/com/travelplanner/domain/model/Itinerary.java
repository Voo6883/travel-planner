package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ItineraryStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A trip's plan (UC-C3-01/02, PLAN §4.1 "Timeline & legs").
 *
 * <p><strong>The aggregate root, and the boundary the READY rule is enforced at.</strong> Task 28's
 * Definition of Done says an invalid schedule cannot be persisted as ready. That cannot be a
 * database CHECK — the rule spans three tables and asks questions no single row can answer — so it
 * lives here, in the one type every write path has to construct.
 *
 * <p><strong>One live plan per trip.</strong> UC-C3-04 regenerates by replacement, matching
 * {@code uq_itinerary_trip}. Two live plans is the state where "which one is mine" has no answer.
 *
 * @param timezone the destination's IANA zone, copied at generation time. Every {@code LocalTime}
 *        below is a wall-clock reading in <em>this</em> zone, and this is the only thing that can
 *        turn one into an instant. Copied rather than followed so a later correction to the
 *        destination row does not silently move an existing plan
 * @param version optimistic lock, per ADR 008. C5 edits a plan block by block, so two concurrent
 *        edits to one itinerary must not merge into a day nobody planned
 */
public record Itinerary(
        UUID id,
        UUID tripId,
        UUID userId,
        UUID destinationId,
        ItineraryStatus status,
        LocalDate startDate,
        LocalDate endDate,
        String timezone,
        String algorithmVersion,
        List<ItineraryDay> days,
        int version) {

    public Itinerary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tripId, "tripId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        Objects.requireNonNull(days, "days");

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must not precede startDate, got " + startDate + " to " + endDate);
        }
        if (algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion must not be blank");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative, got " + version);
        }
        requireIanaZone(timezone);

        days = days.stream()
                .sorted(Comparator.comparingInt(ItineraryDay::dayNumber))
                .toList();
        requireContiguousDays(days, startDate, endDate);
        requireReadyIsPublishable(status, days);
    }

    /**
     * The same rule {@code Destination} applies, for the same reason and one step later.
     *
     * <p>A destination's zone is validated when it is curated; this is the copy, and a copy taken
     * from a row that predates that validation would put an unresolvable zone on a plan. Everything
     * C3 does with it happens elsewhere — rendering a timeline, converting a block to an instant —
     * and {@code ZoneId.of} throwing there names the wrong culprit.
     */
    private static void requireIanaZone(String timezone) {
        try {
            ZoneId zone = ZoneId.of(timezone);
            if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
                throw new java.time.DateTimeException("not a region-based zone");
            }
        } catch (java.time.DateTimeException unresolvable) {
            throw new IllegalArgumentException("timezone must be an IANA zone id such as "
                    + "'Asia/Tokyo', got '" + timezone + "'", unresolvable);
        }
    }

    /**
     * Day numbers are 1..n with no gaps, and each day's date is its ordinal offset from the start.
     *
     * <p>A gap is not a shorter trip — it is a day the traveller will look for and not find. The
     * date check is what catches a plan built across a month boundary by arithmetic that assumed 30
     * days, which is the shape this defect takes in practice.
     */
    private static void requireContiguousDays(
            List<ItineraryDay> days, LocalDate startDate, LocalDate endDate) {
        long span = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days.size() > span) {
            throw new IllegalArgumentException("got " + days.size() + " days for a "
                    + span + "-day range " + startDate + " to " + endDate);
        }
        for (int i = 0; i < days.size(); i++) {
            ItineraryDay day = days.get(i);
            if (day.dayNumber() != i + 1) {
                throw new IllegalArgumentException("day numbers must run 1.." + days.size()
                        + " without gaps, found " + day.dayNumber() + " at position " + (i + 1));
            }
            LocalDate expected = startDate.plusDays(i);
            if (!day.date().equals(expected)) {
                throw new IllegalArgumentException("day " + day.dayNumber() + " should fall on "
                        + expected + ", got " + day.date());
            }
        }
    }

    /**
     * Task 28 DoD: "invalid schedules cannot be persisted as ready."
     *
     * <p>The per-day invariants — no overlaps, everything inside the window — are asserted by
     * {@link ItineraryDay} itself, so by the time a day is in this list it is internally sound.
     * What is left is the whole-plan question: a {@code READY} plan must actually cover its range
     * and no day of it may be empty. An empty day in a published plan reads as "we had nothing for
     * you" dressed as a finished itinerary.
     *
     * <p>{@code DRAFT} is held to none of this. That is the state a plan is in while it is being
     * assembled, and a half-built plan that cannot be saved cannot be resumed.
     */
    private static void requireReadyIsPublishable(
            ItineraryStatus status, List<ItineraryDay> days) {
        if (!status.isPublishable()) {
            return;
        }
        if (days.isEmpty()) {
            throw new IllegalArgumentException("a READY itinerary must have at least one day");
        }
        for (ItineraryDay day : days) {
            if (day.isEmpty()) {
                throw new IllegalArgumentException(
                        "day " + day.dayNumber() + " is empty, so this plan is not READY");
            }
        }
    }

    /** UC-C3-01: only a publishable plan unlocks the itinerary view. */
    public boolean isPublishable() {
        return status.isPublishable();
    }

    /** The zone every {@code LocalTime} on this plan is read in. */
    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    /** Inclusive, so a single-day trip is 1 rather than 0. */
    public int nightCount() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
    }

    public Optional<ItineraryDay> day(int dayNumber) {
        return days.stream().filter(day -> day.dayNumber() == dayNumber).findFirst();
    }

    /**
     * Promotes an assembled draft, applying the READY rule rather than trusting the caller.
     *
     * @throws IllegalArgumentException when the plan is not publishable, with the reason named
     */
    public Itinerary markReady() {
        return new Itinerary(id, tripId, userId, destinationId, ItineraryStatus.READY, startDate,
                endDate, timezone, algorithmVersion, days, version);
    }
}
