package com.travelplanner.domain.port;

import com.travelplanner.domain.model.TripBrief;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link TripBrief}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p>Scoped by {@code tripId} rather than by user: the ownership check happens once, on the parent
 * {@link com.travelplanner.domain.model.Trip}, and a brief cannot exist without one
 * ({@code fk_trip_brief_trip}). Repeating the user filter here would imply the brief is reachable
 * without loading its trip, which would break the documented lock order
 * ({@code trip} then {@code trip_brief}).
 */
public interface TripBriefRepositoryPort {

    /**
     * Inserts or updates. Raises
     * {@link com.travelplanner.domain.exception.VersionConflictException} when a concurrent
     * transaction already advanced the version (ADR 008).
     */
    TripBrief save(TripBrief brief);

    Optional<TripBrief> findByTripId(UUID tripId);
}
