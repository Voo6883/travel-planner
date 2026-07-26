package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.TripBriefEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code trip_brief}. One row per trip ({@code ux_trip_brief_trip_id}). */
public interface TripBriefJpaRepository extends JpaRepository<TripBriefEntity, UUID> {

    Optional<TripBriefEntity> findByTripId(UUID tripId);
}
