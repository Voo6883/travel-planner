package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ItineraryItemCategory;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One scheduled block on a day (UC-C3-03/06/08, TRAVEL-KNOWLEDGE-CATALOG {@code itinerary_item}).
 *
 * <p><strong>The times are wall-clock, in the itinerary's zone.</strong> "The temple at 09:30" is
 * the intent; an instant would pin it to a UTC offset and move the whole plan the next time that
 * zone's DST rules change. {@link Itinerary#timezone()} is what turns these into instants, once, at
 * the point something actually needs one.
 *
 * <p><strong>{@code endsAt} is stored rather than derived from {@code durationMinutes}.</strong>
 * They can disagree — and when they do it is a scheduling defect worth failing on rather than
 * quietly recomputing, because the two facts come from different places: the duration is what the
 * knowledge base says a visit takes, the end is where the scheduler had room to put it.
 *
 * @param poiId absent for {@code FREE_TIME}, and for a meal slot the scheduler held open but could
 *        not fill from the knowledge base. A held slot with no POI is honest; naming a restaurant
 *        nobody curated is the invented fact PLAN §4.1.0 forbids
 * @param title carried at build time so a plan stays readable after its POI is recurated or
 *        withdrawn
 * @param sourceRef UC-C3-03, copied from the POI's provenance so the timeline can cite a source
 *        without re-reading the knowledge base
 */
public record ItineraryItem(
        UUID id,
        int ordinal,
        UUID poiId,
        ItineraryItemCategory category,
        String title,
        LocalTime startsAt,
        LocalTime endsAt,
        int durationMinutes,
        String sourceRef,
        String notes) {

    /** Matches {@code itinerary_item.title varchar(200)}. */
    public static final int MAX_TITLE_LENGTH = 200;

    public ItineraryItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");

        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative, got " + ordinal);
        }
        // ck_itinerary_item_title_not_blank. `not null` does not catch it: PostgreSQL considers ''
        // a perfectly good non-null string, and a blank title renders as an empty timeline card.
        if (title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "title must be 1.." + MAX_TITLE_LENGTH + " characters, got '" + title + "'");
        }
        // ck_itinerary_item_times_ordered. Strictly after: a zero-length block is not a plan, and
        // it would defeat the overlap check below by touching both neighbours at once.
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException(
                    "endsAt must be after startsAt, got " + startsAt + " to " + endsAt);
        }
        if (durationMinutes < 1) {
            throw new IllegalArgumentException(
                    "durationMinutes must be at least 1, got " + durationMinutes);
        }
        // A day does not wrap midnight in this model — `LocalTime` alone cannot express "until
        // 01:00 tomorrow", and pretending otherwise would make every duration comparison wrong.
        // The scheduler refuses such a candidate; this is the assertion that says so out loud.
        if (minutesBetween(startsAt, endsAt) != durationMinutes) {
            throw new IllegalArgumentException("durationMinutes " + durationMinutes
                    + " disagrees with the scheduled window " + startsAt + " to " + endsAt
                    + " (" + minutesBetween(startsAt, endsAt) + " minutes)");
        }
        // A POI-less SIGHT is a block asserting a place with nothing behind it — exactly the
        // ungrounded claim UC-C3-03 exists to prevent. FOOD may hold an unfilled slot; FREE_TIME and
        // TRANSIT have no POI by nature.
        if (category == ItineraryItemCategory.SIGHT && poiId == null) {
            throw new IllegalArgumentException("a SIGHT must be grounded in a POI (UC-C3-03)");
        }
        if (category == ItineraryItemCategory.FREE_TIME && poiId != null) {
            throw new IllegalArgumentException("FREE_TIME must not name a POI");
        }
    }

    /** Absent for free time, and for a meal slot held open but never filled. */
    public Optional<UUID> poiIdIfKnown() {
        return Optional.ofNullable(poiId);
    }

    /** Absent when the block asserts nothing that needs a citation — free time, a transfer. */
    public Optional<String> sourceRefIfPresent() {
        return Optional.ofNullable(sourceRef);
    }

    public Optional<String> notesIfPresent() {
        return Optional.ofNullable(notes);
    }

    /**
     * Whether this block and {@code other} occupy any of the same minute.
     *
     * <p>Touching is not overlapping: a block ending at 11:00 and the next starting at 11:00 are
     * adjacent, which is the normal shape of a day and the reason this cannot be an {@code EXCLUDE}
     * constraint over time ranges in the schema.
     */
    public boolean overlaps(ItineraryItem other) {
        Objects.requireNonNull(other, "other");
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
    }

    /** True when this block fits entirely inside the day's opening window. */
    public boolean fitsWithin(LocalTime windowStart, LocalTime windowEnd) {
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        return !startsAt.isBefore(windowStart) && !endsAt.isAfter(windowEnd);
    }

    private static int minutesBetween(LocalTime from, LocalTime to) {
        return (int) java.time.Duration.between(from, to).toMinutes();
    }
}
