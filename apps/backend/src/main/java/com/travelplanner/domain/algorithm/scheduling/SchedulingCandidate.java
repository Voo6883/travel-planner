package com.travelplanner.domain.algorithm.scheduling;

import com.travelplanner.domain.enums.ItineraryItemCategory;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One place the scheduler may place on a day, with everything it needs to decide whether it fits.
 *
 * <p>The scheduler never reads the knowledge base. A candidate is assembled by the application
 * layer from {@code KnowledgePort} and handed over complete, which is what keeps this package pure
 * and what lets the tests be tables of records rather than fixtures behind a mock.
 *
 * @param opensAt absent when the knowledge base has no curated hours. <strong>Absent is not
 *        open.</strong> The scheduler will still place the candidate — refusing everything with
 *        unknown hours would empty a plan built on a PARTIAL corpus — but it reports
 *        {@code UNKNOWN_HOURS} against it, so "we do not know when this opens" reaches the traveller
 *        instead of being silently resolved in the product's favour
 * @param travelMinutesFromPrevious the buffer to leave before this block when it follows another.
 *        Supplied by the caller from task 29's routing, or zero when unknown — never guessed here
 * @param priority lower sorts earlier when two candidates compete for the same slot. The caller's
 *        ranking, not a preference invented by the scheduler
 */
public record SchedulingCandidate(
        UUID poiId,
        String title,
        ItineraryItemCategory category,
        int visitMinutes,
        LocalTime opensAt,
        LocalTime closesAt,
        UUID areaId,
        String sourceRef,
        int travelMinutesFromPrevious,
        int priority) {

    public SchedulingCandidate {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(category, "category");

        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (visitMinutes < 1) {
            throw new IllegalArgumentException(
                    "visitMinutes must be at least 1, got " + visitMinutes);
        }
        if (travelMinutesFromPrevious < 0) {
            throw new IllegalArgumentException("travelMinutesFromPrevious must not be negative, got "
                    + travelMinutesFromPrevious);
        }
        // Half-known hours are worse than none: "opens at 09:00, closes at ???" invites a caller to
        // treat the missing half as midnight. Both or neither.
        if ((opensAt == null) != (closesAt == null)) {
            throw new IllegalArgumentException("opensAt and closesAt must be set together");
        }
        if (opensAt != null && !closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException(
                    "closesAt must be after opensAt, got " + opensAt + " to " + closesAt);
        }
    }

    /** UC-C3-03: a candidate with no curated hours is placed, and reported. */
    public boolean hasKnownHours() {
        return opensAt != null;
    }

    public Optional<LocalTime> opensAtIfKnown() {
        return Optional.ofNullable(opensAt);
    }

    public Optional<LocalTime> closesAtIfKnown() {
        return Optional.ofNullable(closesAt);
    }

    public Optional<UUID> areaIdIfKnown() {
        return Optional.ofNullable(areaId);
    }

    /**
     * The earliest this candidate may start, given a proposed start.
     *
     * <p>Returns {@code proposed} unchanged when hours are unknown — the scheduler does not invent
     * an opening time, it places the block and flags the uncertainty.
     */
    public LocalTime earliestStartFrom(LocalTime proposed) {
        Objects.requireNonNull(proposed, "proposed");
        if (!hasKnownHours() || !proposed.isBefore(opensAt)) {
            return proposed;
        }
        return opensAt;
    }

    /** Whether a visit beginning at {@code start} would still be inside curated opening hours. */
    public boolean isOpenThroughout(LocalTime start, LocalTime end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!hasKnownHours()) {
            return true;
        }
        return !start.isBefore(opensAt) && !end.isAfter(closesAt);
    }
}
