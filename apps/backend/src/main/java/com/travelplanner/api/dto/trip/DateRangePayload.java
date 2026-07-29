package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.valueobject.DateRange;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * An inclusive span of calendar days, on the wire.
 *
 * <p>{@code LocalDate} rather than an instant, for the reason {@link DateRange} states: "arrive on
 * 3 April" is a calendar fact about the destination, and turning it into a point on the UTC
 * timeline needs a timezone the traveller has not chosen yet — one that would shift the date across
 * a boundary for anybody east of Greenwich.
 *
 * <p>One object rather than two sibling fields on the brief, so a client reads dates as present or
 * absent and never has to decide what a start with no end means. {@code DateRange} refuses that
 * pair, and {@code ck_trip_brief_dates_paired} refuses it in the database.
 */
public record DateRangePayload(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {

    /** @return null when the brief has no dates yet */
    public static DateRangePayload from(DateRange dates) {
        return dates == null ? null : new DateRangePayload(dates.start(), dates.end());
    }

    /** @throws com.travelplanner.domain.exception.ValidationFailedException when end precedes start */
    public DateRange toDateRange() {
        return DateRange.of(startDate, endDate);
    }

    /** Null-tolerant, so an omitted date range does not need a branch at every call site. */
    public static DateRange toDateRange(DateRangePayload payload) {
        return payload == null ? null : payload.toDateRange();
    }
}
