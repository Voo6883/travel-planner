package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ItineraryItemCategory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One day of a plan (UC-C3-02/07/08).
 *
 * <p><strong>Items are held in clock order and the invariant is asserted, not assumed.</strong> The
 * constructor sorts by {@code startsAt} and then checks that no two blocks share a minute, so a day
 * that reaches this type is a day that can actually be walked. The alternative — trusting the
 * scheduler and validating at the boundary — puts the one rule the whole feature exists to uphold
 * somewhere a future caller can skip.
 *
 * @param areaId UC-C3-07's clustering. Absent means "not clustered around one area" — a travel day,
 *        or a destination whose areas were never curated. It never means "unknown"
 * @param windowStart the local wall-clock bounds the day was scheduled against. Kept because they
 *        are an input to the plan rather than a property of it: re-validating a day against a
 *        different window would silently change what "feasible" meant when it was built
 */
public record ItineraryDay(
        UUID id,
        int dayNumber,
        LocalDate date,
        UUID areaId,
        LocalTime windowStart,
        LocalTime windowEnd,
        List<ItineraryItem> items) {

    public ItineraryDay {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        Objects.requireNonNull(items, "items");

        if (dayNumber < 1) {
            // 1-based because "day 1" is the traveller's own vocabulary and what the UI shows.
            throw new IllegalArgumentException("dayNumber must be at least 1, got " + dayNumber);
        }
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("windowEnd must be after windowStart, got "
                    + windowStart + " to " + windowEnd);
        }

        items = items.stream()
                .sorted(Comparator.comparing(ItineraryItem::startsAt))
                .toList();
        requireNoOverlap(items);
        requireWithinWindow(items, windowStart, windowEnd);
    }

    /**
     * Sorted intervals rather than an interval tree (PLAN §4.0.3 names both).
     *
     * <p>A day holds a handful of blocks, so the sort dominates and a tree would cost more to build
     * than it saves. Once sorted, an overlap can only be between neighbours, which makes this one
     * pass and — more usefully — makes the failure message name the two blocks that collide rather
     * than reporting that "the day is invalid".
     */
    private static void requireNoOverlap(List<ItineraryItem> ordered) {
        for (int i = 1; i < ordered.size(); i++) {
            ItineraryItem previous = ordered.get(i - 1);
            ItineraryItem current = ordered.get(i);
            if (previous.overlaps(current)) {
                throw new IllegalArgumentException("'" + previous.title() + "' ("
                        + previous.startsAt() + "-" + previous.endsAt() + ") overlaps '"
                        + current.title() + "' (" + current.startsAt() + "-" + current.endsAt()
                        + ")");
            }
        }
    }

    private static void requireWithinWindow(
            List<ItineraryItem> items, LocalTime windowStart, LocalTime windowEnd) {
        for (ItineraryItem item : items) {
            if (!item.fitsWithin(windowStart, windowEnd)) {
                throw new IllegalArgumentException("'" + item.title() + "' (" + item.startsAt()
                        + "-" + item.endsAt() + ") falls outside the day window " + windowStart
                        + "-" + windowEnd);
            }
        }
    }

    /** Absent when the day is not clustered around a single area (UC-C3-07). */
    public Optional<UUID> areaIdIfKnown() {
        return Optional.ofNullable(areaId);
    }

    /** Minutes actually committed to blocks — the input to the pace rule. */
    public int scheduledMinutes() {
        return items.stream().mapToInt(ItineraryItem::durationMinutes).sum();
    }

    /**
     * UC-C3-06. A day with no meal is a scheduling defect rather than a preference, so the question
     * is asked of the day rather than left to whoever renders it.
     */
    public boolean hasMealSlot() {
        return items.stream().anyMatch(item -> item.category() == ItineraryItemCategory.FOOD);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
