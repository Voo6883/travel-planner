package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.port.ItineraryRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.ItineraryEntity;
import com.travelplanner.infrastructure.persistence.mapper.ItineraryPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.ItineraryJpaRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * {@link ItineraryRepositoryPort} over JPA. The only place {@link ItineraryEntity} is built.
 *
 * <p><strong>Regeneration replaces in place.</strong> UC-C3-04 produces a new plan for a trip that
 * already has one, and {@code uq_itinerary_trip} permits exactly one. Rather than delete-then-insert
 * — two statements, a window in between where the trip has no plan, and a unique-violation race if
 * anything else inserts — the existing row is loaded and rewritten, with {@code orphanRemoval}
 * clearing the days the new plan does not have. A five-day plan replaced by a three-day one leaves
 * nothing behind.
 *
 * <p>A lost optimistic lock becomes {@link VersionConflictException} (ADR 008), which is what stops
 * two C5 chat edits merging into a day nobody planned.
 *
 * <p>No {@code @Transactional} here: adapters join the service's transaction (PLAN §4.0.2-H).
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class ItineraryRepositoryAdapter implements ItineraryRepositoryPort {

    private final ItineraryJpaRepository repository;
    private final ItineraryPersistenceMapper mapper;
    private final Clock clock;

    public ItineraryRepositoryAdapter(
            ItineraryJpaRepository repository, ItineraryPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = Clock.systemUTC();
    }

    @Override
    public Itinerary save(Itinerary itinerary) {
        // Matched on trip rather than on id: a regenerated plan is a new aggregate with a new id for
        // the same trip, and looking it up by id would insert a second row the unique index refuses.
        ItineraryEntity existing = repository.findByTripId(itinerary.tripId()).orElse(null);
        if (existing != null && !existing.getId().equals(itinerary.id())) {
            // Replacing the plan wholesale. Flushing the delete first keeps the unique index
            // satisfied at every point rather than only at commit.
            repository.delete(existing);
            repository.flush();
            existing = null;
        }
        try {
            ItineraryEntity saved = repository.save(
                    mapper.toEntity(itinerary, existing, clock.instant()));
            return mapper.toDomain(saved);
        } catch (OptimisticLockingFailureException lost) {
            throw new VersionConflictException(itinerary.version());
        }
    }

    @Override
    public Optional<Itinerary> findByTripIdAndUserId(UUID tripId, UUID userId) {
        return repository.findByTripIdAndUserId(tripId, userId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByTripId(UUID tripId) {
        return repository.existsByTripId(tripId);
    }

    @Override
    public void deleteByTripId(UUID tripId) {
        repository.deleteByTripId(tripId);
    }
}
