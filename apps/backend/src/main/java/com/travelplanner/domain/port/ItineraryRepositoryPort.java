package com.travelplanner.domain.port;

import com.travelplanner.domain.model.Itinerary;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link Itinerary}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p><strong>The whole aggregate, always.</strong> There is no {@code saveDay} or {@code saveItem}:
 * the invariants that make a plan valid — no overlaps, every block inside its window, no empty day
 * in a published plan — span all three tables, and a port that could write one day in isolation
 * would be a way to persist a plan no constructor ever checked.
 *
 * <p>Reads are user-scoped for the same reason {@link TripRepositoryPort}'s are: a plan is personal
 * data, and a finder that takes only a trip id is one call site away from serving somebody else's
 * itinerary.
 */
public interface ItineraryRepositoryPort {

    /**
     * Inserts or replaces the trip's plan.
     *
     * <p>UC-C3-04 regenerates by replacement — {@code uq_itinerary_trip} allows exactly one live
     * plan per trip — so saving a new plan for a trip that already has one supersedes it rather
     * than failing.
     *
     * @throws com.travelplanner.domain.exception.VersionConflictException on a concurrent write to
     *         the same plan (ADR 008), which is what stops two C5 chat edits merging into a day
     *         nobody planned
     */
    Itinerary save(Itinerary itinerary);

    /** The trip's plan, but only within a trip the caller was already shown to own. */
    Optional<Itinerary> findByTripIdAndUserId(UUID tripId, UUID userId);

    /** Whether a plan exists at all, without paying to assemble the days. */
    boolean existsByTripId(UUID tripId);

    /**
     * Removes the trip's plan, if any.
     *
     * <p>Needed by regeneration: replacing a five-day plan with a three-day one must not leave days
     * four and five behind. The cascade from {@code itinerary} does the rest.
     */
    void deleteByTripId(UUID tripId);
}
