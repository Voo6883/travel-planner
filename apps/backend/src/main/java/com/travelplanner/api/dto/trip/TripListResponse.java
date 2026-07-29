package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.model.Trip;
import java.util.List;

/**
 * The body of {@code GET /trips}.
 *
 * <p>An object rather than a bare array, for the reason {@code SupportedDestinations} gives: a
 * top-level array cannot gain a field, and this response will want one — page metadata when a
 * single user's trip list stops being a screenful. Wrapping now costs one key; wrapping later
 * breaks every generated client.
 *
 * <p>Not paginated yet, deliberately. One person's trips are a list they scroll, not a corpus, and
 * shipping {@code page}/{@code page_size}/{@code total} that no client varies would be three fields
 * of ceremony. The key stays {@code trips} when paging arrives.
 */
public record TripListResponse(List<TripResponse> trips) {

    public static TripListResponse from(List<Trip> trips) {
        return new TripListResponse(trips.stream().map(TripResponse::from).toList());
    }
}
