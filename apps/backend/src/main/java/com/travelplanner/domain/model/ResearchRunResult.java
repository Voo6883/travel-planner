package com.travelplanner.domain.model;

import com.travelplanner.domain.algorithm.ranking.ExcludedDestination;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable outcome of one research run (UC-C2-05 typed empty + UC-C2-03 ranked list).
 *
 * <p>Exactly one of: {@link #noConfidentResult()} with an empty {@link #recommendations()}, or a
 * non-empty recommendation list. Prompt/model metadata describes the narrative half only — the
 * numeric scores come from {@link #algorithmVersion()}.
 */
public record ResearchRunResult(
        UUID researchRunId,
        UUID tripId,
        UUID userId,
        boolean noConfidentResult,
        String algorithmVersion,
        String promptTemplateId,
        int promptVersion,
        String modelName,
        List<ExcludedDestination> excluded,
        List<RankedRecommendation> recommendations,
        Instant createdAt) {

    public ResearchRunResult {
        Objects.requireNonNull(researchRunId, "researchRunId");
        Objects.requireNonNull(tripId, "tripId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        Objects.requireNonNull(promptTemplateId, "promptTemplateId");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(createdAt, "createdAt");
        excluded = excluded == null ? List.of() : List.copyOf(excluded);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        if (algorithmVersion.isBlank() || promptTemplateId.isBlank() || modelName.isBlank()) {
            throw new IllegalArgumentException("algorithm/prompt/model labels must not be blank");
        }
        if (promptVersion < 0) {
            throw new IllegalArgumentException("promptVersion must not be negative");
        }
        if (noConfidentResult != recommendations.isEmpty()) {
            throw new IllegalArgumentException(
                    "noConfidentResult must equal recommendations.isEmpty()");
        }
    }

    public static ResearchRunResult noConfident(
            UUID researchRunId,
            UUID tripId,
            UUID userId,
            String algorithmVersion,
            String promptTemplateId,
            int promptVersion,
            String modelName,
            List<ExcludedDestination> excluded,
            Instant createdAt) {
        return new ResearchRunResult(
                researchRunId, tripId, userId, true, algorithmVersion, promptTemplateId,
                promptVersion, modelName, excluded, List.of(), createdAt);
    }

    public static ResearchRunResult ranked(
            UUID researchRunId,
            UUID tripId,
            UUID userId,
            String algorithmVersion,
            String promptTemplateId,
            int promptVersion,
            String modelName,
            List<ExcludedDestination> excluded,
            List<RankedRecommendation> recommendations,
            Instant createdAt) {
        return new ResearchRunResult(
                researchRunId, tripId, userId, false, algorithmVersion, promptTemplateId,
                promptVersion, modelName, excluded, recommendations, createdAt);
    }
}
