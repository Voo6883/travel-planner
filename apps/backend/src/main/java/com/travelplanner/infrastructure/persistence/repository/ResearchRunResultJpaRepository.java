package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.ResearchRunResultEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code research_run_result}. */
public interface ResearchRunResultJpaRepository extends JpaRepository<ResearchRunResultEntity, UUID> {

    Optional<ResearchRunResultEntity> findByResearchRunIdAndUserId(UUID researchRunId, UUID userId);

    Optional<ResearchRunResultEntity> findFirstByTripIdAndUserIdOrderByCreatedAtDesc(
            UUID tripId, UUID userId);
}
