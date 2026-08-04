package com.travelplanner.domain.model;

import com.travelplanner.domain.algorithm.ranking.ScoreBreakdown;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One scored destination proposal from a research run (PLAN §4.1, UC-C2-03/04/10).
 *
 * <p>{@link #fitScore()} and the rest of {@link #breakdown()} come from {@code DestinationRanker}
 * — never from the LLM. The agent may only author {@link #rationale()}, {@link #travelerGuide()},
 * {@link #risks()}, and {@link #bestWindow()}.
 */
public record RankedRecommendation(
        UUID id,
        UUID tripId,
        UUID userId,
        UUID researchRunId,
        UUID destinationId,
        String destinationSlug,
        String countryCode,
        int rank,
        ScoreBreakdown breakdown,
        String rationale,
        TravelerGuide travelerGuide,
        List<String> risks,
        String bestWindow,
        List<RecommendationSourceRef> sourceRefs,
        String algorithmVersion,
        Instant createdAt) {

    public RankedRecommendation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tripId, "tripId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(researchRunId, "researchRunId");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(destinationSlug, "destinationSlug");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(breakdown, "breakdown");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(travelerGuide, "travelerGuide");
        Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        Objects.requireNonNull(createdAt, "createdAt");
        risks = risks == null ? List.of() : List.copyOf(risks);
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        bestWindow = blankToNull(bestWindow);
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be at least 1, got " + rank);
        }
        if (destinationSlug.isBlank()) {
            throw new IllegalArgumentException("destinationSlug must not be blank");
        }
        if (countryCode.length() != 2) {
            throw new IllegalArgumentException("countryCode must be ISO alpha-2");
        }
        if (rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
        if (algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion must not be blank");
        }
        if (sourceRefs.isEmpty()) {
            throw new IllegalArgumentException("sourceRefs must not be empty");
        }
    }

    public double fitScore() {
        return breakdown.fitScore();
    }

    public Optional<Money> estimatedCostIfPresent() {
        return breakdown.estimatedCostIfPresent();
    }

    public Optional<String> bestWindowIfPresent() {
        return Optional.ofNullable(bestWindow);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
