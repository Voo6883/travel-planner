package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.Trip;
import java.time.Instant;
import java.util.UUID;

/**
 * One trip, as every trip endpoint returns it.
 *
 * <p>{@code version} is published on every read because ADR 008 §1 requires the read that precedes
 * a write to carry it — a client that cannot see the version cannot send {@code expected_version},
 * and would have no choice but to force-overwrite.
 *
 * <p>{@code status} is read-only. It is derived from brief completeness and moved to
 * {@code ARCHIVED} by the archive action; there is no request body anywhere that accepts it, so a
 * client cannot declare {@code BRIEF_COMPLETE} over an empty brief and step past the C2 gate.
 *
 * <p>No {@code user_id}. Ownership comes from the session on every request, and echoing the owner
 * back would publish an identifier that no endpoint on this API accepts as input.
 */
public record TripResponse(
        UUID tripId,
        String name,
        TripStatus status,
        UUID selectedRecommendationId,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static TripResponse from(Trip trip) {
        return new TripResponse(trip.id(), trip.name(), trip.status(),
                trip.selectedRecommendationId(), trip.version(), trip.createdAt(),
                trip.updatedAt());
    }
}
