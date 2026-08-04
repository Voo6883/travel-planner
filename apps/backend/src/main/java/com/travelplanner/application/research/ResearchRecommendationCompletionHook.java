package com.travelplanner.application.research;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.port.RankedRecommendationRepositoryPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists the research outcome in the same transaction that marks the job COMPLETED and moves the
 * trip to {@code RESEARCH_READY} (tasks/23 completion-hook contract).
 *
 * <p><strong>Persistence only — no LLM, no HTTP.</strong> The expensive work already happened in
 * {@link TravelResearchJobHandler}. Registered as a scanned {@code @Component} so it wins over the
 * {@code @ConditionalOnMissingBean} no-op in {@code ResearchExecutionConfig}.
 */
@Component
@RequiresDatabase
public class ResearchRecommendationCompletionHook implements ResearchCompletionHook {

    private static final Logger log = LoggerFactory.getLogger(ResearchRecommendationCompletionHook.class);

    private final PendingResearchOutcomeStore pending;
    private final RankedRecommendationRepositoryPort recommendations;

    public ResearchRecommendationCompletionHook(
            PendingResearchOutcomeStore pending,
            RankedRecommendationRepositoryPort recommendations) {
        this.pending = pending;
        this.recommendations = recommendations;
    }

    @Override
    public void onResearchCompleted(UUID jobId, UUID tripId) {
        ResearchRunResult result = pending.take(jobId).orElseThrow(() ->
                new IllegalStateException("research outcome missing for job " + jobId
                        + " — refusing RESEARCH_READY without a durable marker"));
        if (!result.tripId().equals(tripId)) {
            throw new IllegalStateException("research outcome trip mismatch for job " + jobId);
        }
        recommendations.save(result);
        log.info("research_recommendations_persisted job={} trip={} run={} no_confident={} count={}",
                jobId, tripId, result.researchRunId(), result.noConfidentResult(),
                result.recommendations().size());
    }
}
