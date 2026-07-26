package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.TripBriefEntity;
import com.travelplanner.infrastructure.persistence.mapper.TripBriefPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.TripBriefJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * {@link TripBriefRepositoryPort} over JPA.
 *
 * <p>Same detached-merge write path as {@code TripRepositoryAdapter}, and for the same reason: the
 * brief is the aggregate ADR 008 was written about, since the debounced form and the agent's
 * {@code update_trip_brief} tool both write it.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class TripBriefRepositoryAdapter implements TripBriefRepositoryPort {

    private final TripBriefJpaRepository repository;
    private final TripBriefPersistenceMapper mapper;

    public TripBriefRepositoryAdapter(TripBriefJpaRepository repository,
            TripBriefPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public TripBrief save(TripBrief brief) {
        TripBriefEntity entity = new TripBriefEntity();
        mapper.applyToEntity(brief, entity);
        try {
            return mapper.toDomain(repository.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException conflict) {
            throw new VersionConflictException(brief.version() + 1);
        }
    }

    @Override
    public Optional<TripBrief> findByTripId(UUID tripId) {
        return repository.findByTripId(tripId).map(mapper::toDomain);
    }
}
