package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.port.ResearchJobRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.ResearchJobEntity;
import com.travelplanner.infrastructure.persistence.mapper.ResearchJobPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.ResearchJobJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * {@link ResearchJobRepositoryPort} over JPA. The only place {@link ResearchJobEntity} is built.
 *
 * <p>Writes go through a <em>detached</em> entity so Hibernate's {@code UPDATE ... WHERE version = ?}
 * enforces the ADR 008 lock, exactly as {@code TripRepositoryAdapter} does.
 *
 * <p>Two database guarantees are translated into typed domain failures here: a lost optimistic lock
 * becomes {@link VersionConflictException}, and a violation of {@code uq_research_job_active_per_trip}
 * — a second concurrent start racing past the service's check-then-insert — becomes the same
 * {@code validation_failed} the service raises on the friendly path, so a true race and a stale
 * client get one answer.
 *
 * <p>No {@code @Transactional} here: adapters join the service's transaction (PLAN §4.0.2-H).
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class ResearchJobRepositoryAdapter implements ResearchJobRepositoryPort {

    private final ResearchJobJpaRepository repository;
    private final ResearchJobPersistenceMapper mapper;

    public ResearchJobRepositoryAdapter(ResearchJobJpaRepository repository,
            ResearchJobPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ResearchJob save(ResearchJob job) {
        ResearchJobEntity entity = new ResearchJobEntity();
        mapper.applyToEntity(job, entity);
        try {
            return mapper.toDomain(repository.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException conflict) {
            throw new VersionConflictException(job.version() + 1);
        } catch (DataIntegrityViolationException duplicate) {
            // The partial unique index refused a second active job for this trip.
            throw ValidationFailedException.field("status",
                    "a research job is already active for this trip");
        }
    }

    @Override
    public Optional<ResearchJob> findById(UUID jobId) {
        return repository.findById(jobId).map(mapper::toDomain);
    }

    @Override
    public Optional<ResearchJob> findByIdAndTripId(UUID jobId, UUID tripId) {
        return repository.findByIdAndTripId(jobId, tripId).map(mapper::toDomain);
    }

    @Override
    public Optional<ResearchJob> findActiveByTripId(UUID tripId) {
        return repository.findFirstByTripIdAndStatusInOrderByCreatedAtDesc(
                tripId, List.of(ResearchJobStatus.QUEUED, ResearchJobStatus.RUNNING))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<ResearchJob> findLatestByTripId(UUID tripId) {
        return repository.findFirstByTripIdOrderByCreatedAtDesc(tripId).map(mapper::toDomain);
    }

    @Override
    public List<ResearchJob> findByStatus(ResearchJobStatus status) {
        return repository.findByStatus(status).stream().map(mapper::toDomain).toList();
    }
}
