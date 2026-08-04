package com.travelplanner.application.trip;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory ports for the trip use cases, no Spring and no database.
 *
 * <p><strong>The fakes reproduce JPA's {@code @Version} semantics, not just its storage.</strong>
 * {@code save} increments the version and refuses a write whose version is not the stored one,
 * exactly as Hibernate's {@code UPDATE ... WHERE version = ?} does. A fake that ignored the version
 * would let every optimistic-locking test pass against a repository that has no locking at all,
 * which is the failure mode ADR 008 exists to prevent — so the fake is where it has to be modelled
 * rather than mocked away.
 */
final class TripTestFakes {

    private TripTestFakes() {
    }

    static Destination coveredDestination(String slug) {
        return new Destination(UUID.randomUUID(), slug, slug, "MY", "Asia/Kuala_Lumpur",
                null, null, CoverageLevel.FULL);
    }

    /** {@link TripRepositoryPort} over a map, with the ADR 008 optimistic lock modelled. */
    static final class InMemoryTrips implements TripRepositoryPort {

        private final Map<UUID, Trip> rows = new LinkedHashMap<>();

        @Override
        public Trip save(Trip trip) {
            Trip stored = rows.get(trip.id());
            if (stored != null && stored.version() != trip.version()) {
                throw new VersionConflictException(stored.version());
            }
            Trip persisted = new Trip(trip.id(), trip.userId(), trip.name(), trip.status(),
                    trip.selectedRecommendationId(), trip.version() + 1, trip.createdAt(),
                    trip.updatedAt());
            rows.put(persisted.id(), persisted);
            return persisted;
        }

        @Override
        public Optional<Trip> findByIdAndUserId(UUID tripId, UUID userId) {
            return Optional.ofNullable(rows.get(tripId)).filter(trip -> trip.isOwnedBy(userId));
        }

        @Override
        public List<Trip> findAllByUserId(UUID userId) {
            List<Trip> owned = new ArrayList<>(rows.values().stream()
                    .filter(trip -> trip.isOwnedBy(userId))
                    .toList());
            owned.sort(Comparator.comparing(Trip::createdAt).reversed());
            return owned;
        }

        @Override
        public boolean deleteByIdAndUserId(UUID tripId, UUID userId) {
            return findByIdAndUserId(tripId, userId)
                    .map(trip -> rows.remove(trip.id()) != null)
                    .orElse(false);
        }

        /** Writes a row without going through the version check, to set up a starting state. */
        void seed(Trip trip) {
            rows.put(trip.id(), trip);
        }

        Trip stored(UUID tripId) {
            return rows.get(tripId);
        }
    }

    /** {@link TripBriefRepositoryPort} over a map, with the same locking semantics. */
    static final class InMemoryBriefs implements TripBriefRepositoryPort {

        private final Map<UUID, TripBrief> byTripId = new LinkedHashMap<>();

        @Override
        public TripBrief save(TripBrief brief) {
            TripBrief stored = byTripId.get(brief.tripId());
            if (stored != null && stored.version() != brief.version()) {
                throw new VersionConflictException(stored.version());
            }
            TripBrief persisted = new TripBrief(brief.id(), brief.tripId(), brief.destinations(),
                    brief.surpriseMe(), brief.dates(), brief.dateFlexibility(),
                    brief.departureCity(), brief.budget(), brief.party(), brief.interests(),
                    brief.pace(), brief.version() + 1,
                    brief.createdAt(), brief.updatedAt());
            byTripId.put(persisted.tripId(), persisted);
            return persisted;
        }

        @Override
        public Optional<TripBrief> findByTripId(UUID tripId) {
            return Optional.ofNullable(byTripId.get(tripId));
        }

        void seed(TripBrief brief) {
            byTripId.put(brief.tripId(), brief);
        }

        void forget(UUID tripId) {
            byTripId.remove(tripId);
        }

        TripBrief stored(UUID tripId) {
            return byTripId.get(tripId);
        }
    }
}
