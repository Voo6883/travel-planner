package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.model.ItineraryLeg;
import com.travelplanner.domain.port.ItineraryLegRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.ItineraryLegEntity;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.travelplanner.infrastructure.persistence.repository.ItineraryLegJpaRepository;

/**
 * {@link ItineraryLegRepositoryPort} over JPA. The only place {@link ItineraryLegEntity} is built.
 *
 * <p>Mapping is by hand and inline: the shape is flat, there is one array column, and a MapStruct
 * mapper for eleven fields would be a file to keep in step for no benefit.
 *
 * <p>No {@code @Transactional} here — adapters join the service's transaction (PLAN §4.0.2-H). That
 * matters for {@link #replaceForDay}: the delete and the insert have to be one unit, or a failed
 * re-resolution leaves a day with no legs at all.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class ItineraryLegRepositoryAdapter implements ItineraryLegRepositoryPort {

    private final ItineraryLegJpaRepository repository;
    private final Clock clock = Clock.systemUTC();

    public ItineraryLegRepositoryAdapter(ItineraryLegJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ItineraryLeg> replaceForDay(UUID itineraryDayId, List<ItineraryLeg> legs) {
        repository.deleteByItineraryDayId(itineraryDayId);
        // Flushed before inserting so uq_itinerary_leg_pair sees the deletes first. Without it,
        // Hibernate is free to order the insert before the delete and the unique index rejects a
        // re-resolution that is entirely legitimate.
        repository.flush();
        List<ItineraryLegEntity> saved = repository.saveAll(
                legs.stream().map(leg -> toEntity(itineraryDayId, leg)).toList());
        return saved.stream().map(this::toDomain).toList();
    }

    @Override
    public List<ItineraryLeg> findByDayId(UUID itineraryDayId) {
        return repository.findByItineraryDayId(itineraryDayId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ItineraryLeg> findByDayIds(List<UUID> itineraryDayIds) {
        if (itineraryDayIds.isEmpty()) {
            return List.of();
        }
        return repository.findByItineraryDayIdIn(itineraryDayIds).stream()
                .map(this::toDomain)
                .toList();
    }

    private ItineraryLegEntity toEntity(UUID itineraryDayId, ItineraryLeg leg) {
        ItineraryLegEntity entity = new ItineraryLegEntity();
        entity.setId(leg.id());
        entity.setItineraryDayId(itineraryDayId);
        entity.setFromItemId(leg.fromItemId());
        entity.setToItemId(leg.toItemId());
        entity.setResolution(leg.resolution());
        entity.setTransportMode(leg.mode());
        entity.setDurationMinutes(leg.durationMinutes());
        entity.setCostBand(leg.costBand());
        entity.setRouteSegmentId(leg.routeSegmentId());
        entity.setSourceRef(leg.sourceRef());
        entity.setInstructions(leg.instructions());
        entity.setRecommendedAppIds(leg.recommendedAppIds().toArray(new UUID[0]));
        entity.setCreatedAt(clock.instant());
        return entity;
    }

    /** Rebuilds the record, so every honesty invariant is re-checked on the way out. */
    private ItineraryLeg toDomain(ItineraryLegEntity entity) {
        UUID[] apps = entity.getRecommendedAppIds();
        return new ItineraryLeg(entity.getId(), entity.getFromItemId(), entity.getToItemId(),
                entity.getResolution(), entity.getTransportMode(), entity.getDurationMinutes(),
                entity.getCostBand(), entity.getRouteSegmentId(), entity.getSourceRef(),
                entity.getInstructions(), apps == null ? List.of() : List.of(apps));
    }
}
