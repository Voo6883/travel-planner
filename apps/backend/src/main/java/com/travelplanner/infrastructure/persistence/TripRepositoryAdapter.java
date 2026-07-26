package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.TripEntity;
import com.travelplanner.infrastructure.persistence.mapper.TripPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.TripJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * {@link TripRepositoryPort} over JPA. The only place {@link TripEntity} is constructed.
 *
 * <p>Writes always go through a <em>detached</em> entity built from the domain object. That is what
 * makes optimistic locking work: Hibernate compares the detached {@code version} against the stored
 * row during {@code merge} and refuses the write if they differ. Loading the managed entity first
 * and assigning its version field instead would look equivalent and silently disable the check —
 * Hibernate takes the version for the {@code UPDATE ... WHERE version = ?} predicate from the
 * snapshot it loaded, not from whatever the field currently holds.
 *
 * <p>No {@code @Transactional} here. Adapters join the service's transaction (PLAN §4.0.2-H).
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class TripRepositoryAdapter implements TripRepositoryPort {

    private final TripJpaRepository repository;
    private final TripPersistenceMapper mapper;

    public TripRepositoryAdapter(TripJpaRepository repository, TripPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Trip save(Trip trip) {
        TripEntity entity = new TripEntity();
        mapper.applyToEntity(trip, entity);
        try {
            return mapper.toDomain(repository.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException conflict) {
            // The winner of the race advanced the row from the version this write was based on by
            // exactly one, so the current version is derivable without a second query. Re-reading
            // would need REQUIRES_NEW — the transaction is already rollback-only — and PLAN
            // §4.0.2-H forbids an adapter from opening its own propagation.
            throw new VersionConflictException(trip.version() + 1);
        }
    }

    @Override
    public Optional<Trip> findByIdAndUserId(UUID tripId, UUID userId) {
        return repository.findByIdAndUserId(tripId, userId).map(mapper::toDomain);
    }

    @Override
    public List<Trip> findAllByUserId(UUID userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean deleteByIdAndUserId(UUID tripId, UUID userId) {
        return repository.deleteByIdAndUserId(tripId, userId) > 0;
    }
}
