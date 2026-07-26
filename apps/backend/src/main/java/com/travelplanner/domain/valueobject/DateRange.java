package com.travelplanner.domain.valueobject;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An inclusive span of calendar days — travel dates, a hotel stay, an availability window.
 *
 * <p>{@link LocalDate}, not {@code Instant}: "arrive on 3 April" is a calendar fact about the
 * destination, not a point on the UTC timeline. Turning it into an instant requires a timezone the
 * traveller has not chosen yet and would shift the date across a boundary. Timestamps that record
 * *when something happened* use {@code Instant}; dates the user picked stay {@code LocalDate}.
 *
 * <p>Both ends are inclusive, so a single-day trip is {@code start == end} and {@link #days()} is
 * 1. {@link #nights()} is the hotel-facing count and is one lower.
 */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start == null || end == null) {
            throw ValidationFailedException.field("date_range", "start and end are both required");
        }
        if (end.isBefore(start)) {
            throw ValidationFailedException.field("date_range", "end must not be before start");
        }
    }

    public static DateRange of(LocalDate start, LocalDate end) {
        return new DateRange(start, end);
    }

    /** A range covering exactly one day. */
    public static DateRange singleDay(LocalDate day) {
        return new DateRange(day, day);
    }

    /** Inclusive day count — 1 for a single-day range. */
    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** Nights of accommodation the range implies — 0 for a single-day range. */
    public long nights() {
        return ChronoUnit.DAYS.between(start, end);
    }

    public boolean contains(LocalDate day) {
        return day != null && !day.isBefore(start) && !day.isAfter(end);
    }

    /** True when the two ranges share at least one day. Inclusive on both ends. */
    public boolean overlaps(DateRange other) {
        return other != null && !start.isAfter(other.end) && !other.start.isAfter(end);
    }
}
