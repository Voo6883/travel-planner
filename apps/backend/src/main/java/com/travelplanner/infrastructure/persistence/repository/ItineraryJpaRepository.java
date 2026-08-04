package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.ItineraryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code itinerary}. Reached only through {@code ItineraryRepositoryAdapter}. */
public interface ItineraryJpaRepository extends JpaRepository<ItineraryEntity, UUID> {

    /**
     * User-scoped by construction. A finder taking only {@code tripId} would be one call site away
     * from serving somebody else's plan, so the only read that exists carries both.
     */
    Optional<ItineraryEntity> findByTripIdAndUserId(UUID tripId, UUID userId);

    Optional<ItineraryEntity> findByTripId(UUID tripId);

    boolean existsByTripId(UUID tripId);

    void deleteByTripId(UUID tripId);
}
