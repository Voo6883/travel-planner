package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.port.RankedRecommendationRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.RankedRecommendationEntity;
import com.travelplanner.infrastructure.persistence.entity.ResearchRunResultEntity;
import com.travelplanner.infrastructure.persistence.mapper.ResearchRecommendationJsonMapper;
import com.travelplanner.infrastructure.persistence.repository.RankedRecommendationJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.ResearchRunResultJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link RankedRecommendationRepositoryPort} over JPA (V25).
 *
 * <p>Writes the run-result row first, then recommendation rows, so the FK from
 * {@code ranked_recommendation.research_run_id} is satisfied. No {@code @Transactional} here —
 * adapters join the completion hook's transaction.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class RankedRecommendationRepositoryAdapter implements RankedRecommendationRepositoryPort {

    private final ResearchRunResultJpaRepository runs;
    private final RankedRecommendationJpaRepository recommendations;
    private final ResearchRecommendationJsonMapper mapper;

    public RankedRecommendationRepositoryAdapter(
            ResearchRunResultJpaRepository runs,
            RankedRecommendationJpaRepository recommendations,
            ResearchRecommendationJsonMapper mapper) {
        this.runs = runs;
        this.recommendations = recommendations;
        this.mapper = mapper;
    }

    @Override
    public ResearchRunResult save(ResearchRunResult result) {
        ResearchRunResultEntity runEntity = new ResearchRunResultEntity();
        mapper.applyRunResult(result, runEntity);
        runs.saveAndFlush(runEntity);

        List<RankedRecommendation> saved = new ArrayList<>();
        for (RankedRecommendation recommendation : result.recommendations()) {
            RankedRecommendationEntity entity = new RankedRecommendationEntity();
            mapper.applyRecommendation(recommendation, entity);
            saved.add(mapper.toRecommendation(recommendations.saveAndFlush(entity)));
        }
        return new ResearchRunResult(
                result.researchRunId(),
                result.tripId(),
                result.userId(),
                result.noConfidentResult(),
                result.algorithmVersion(),
                result.promptTemplateId(),
                result.promptVersion(),
                result.modelName(),
                result.excluded(),
                saved,
                result.createdAt());
    }

    @Override
    public Optional<ResearchRunResult> findRunByResearchRunId(UUID researchRunId, UUID userId) {
        return runs.findByResearchRunIdAndUserId(researchRunId, userId).map(this::toDomain);
    }

    @Override
    public Optional<ResearchRunResult> findLatestRunByTripId(UUID tripId, UUID userId) {
        return runs.findFirstByTripIdAndUserIdOrderByCreatedAtDesc(tripId, userId).map(this::toDomain);
    }

    @Override
    public List<RankedRecommendation> findByResearchRunId(UUID researchRunId, UUID userId) {
        return recommendations.findByResearchRunIdAndUserIdOrderByRankAsc(researchRunId, userId)
                .stream()
                .map(mapper::toRecommendation)
                .toList();
    }

    @Override
    public Optional<RankedRecommendation> findByIdAndUserId(UUID recommendationId, UUID userId) {
        return recommendations.findByIdAndUserId(recommendationId, userId)
                .map(mapper::toRecommendation);
    }

    private ResearchRunResult toDomain(ResearchRunResultEntity entity) {
        List<RankedRecommendation> rows = findByResearchRunId(entity.getResearchRunId(), entity.getUserId());
        return new ResearchRunResult(
                entity.getResearchRunId(),
                entity.getTripId(),
                entity.getUserId(),
                entity.isNoConfidentResult(),
                entity.getAlgorithmVersion(),
                entity.getPromptTemplateId(),
                entity.getPromptVersion(),
                entity.getModelName(),
                mapper.readExcluded(entity.getExcludedJson()),
                rows,
                entity.getCreatedAt());
    }
}
