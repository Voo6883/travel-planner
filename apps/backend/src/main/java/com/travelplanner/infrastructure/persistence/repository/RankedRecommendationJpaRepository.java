package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.RankedRecommendationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code ranked_recommendation}. */
public interface RankedRecommendationJpaRepository
        extends JpaRepository<RankedRecommendationEntity, UUID> {

    List<RankedRecommendationEntity> findByResearchRunIdAndUserIdOrderByRankAsc(
            UUID researchRunId, UUID userId);

    Optional<RankedRecommendationEntity> findByIdAndUserId(UUID id, UUID userId);
}
