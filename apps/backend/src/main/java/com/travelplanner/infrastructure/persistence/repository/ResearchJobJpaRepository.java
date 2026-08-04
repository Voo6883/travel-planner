package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.infrastructure.persistence.entity.ResearchJobEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code research_job}.
 *
 * <p>{@link JpaRepository#findById(Object)} is used by the background worker, which was handed the
 * job id it minted — the deliberate exception to the "every finder carries the owner" rule that
 * {@code TripJpaRepository} follows, because the worker has no session. The user-facing poll uses
 * {@link #findByIdAndTripId}, which the service reaches only after the trip's ownership is proven.
 */
public interface ResearchJobJpaRepository extends JpaRepository<ResearchJobEntity, UUID> {

    Optional<ResearchJobEntity> findByIdAndTripId(UUID id, UUID tripId);

    Optional<ResearchJobEntity> findFirstByTripIdAndStatusInOrderByCreatedAtDesc(
            UUID tripId, Collection<ResearchJobStatus> statuses);

    Optional<ResearchJobEntity> findFirstByTripIdOrderByCreatedAtDesc(UUID tripId);

    List<ResearchJobEntity> findByStatus(ResearchJobStatus status);
}
