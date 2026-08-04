package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.ItineraryLegEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code itinerary_leg}. Reached only through the adapter. */
public interface ItineraryLegJpaRepository extends JpaRepository<ItineraryLegEntity, UUID> {

    List<ItineraryLegEntity> findByItineraryDayId(UUID itineraryDayId);

    List<ItineraryLegEntity> findByItineraryDayIdIn(List<UUID> itineraryDayIds);

    void deleteByItineraryDayId(UUID itineraryDayId);
}
