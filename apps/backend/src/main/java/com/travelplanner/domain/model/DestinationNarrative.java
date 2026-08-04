package com.travelplanner.domain.model;

import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * LLM/stub narrative for one ranked destination — never carries a fit score.
 *
 * <p>The application merges this with {@code RankedDestination.breakdown()} when building a
 * {@link RankedRecommendation}. Guardrails reject invented destinations, POIs, prices, apps, or
 * source refs against the tool ledger collected during the run.
 */
public record DestinationNarrative(
        UUID destinationId,
        String slug,
        String rationale,
        TravelerGuide travelerGuide,
        List<String> risks,
        String bestWindow,
        List<RecommendationSourceRef> sourceRefs) {

    public DestinationNarrative {
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(travelerGuide, "travelerGuide");
        if (slug.isBlank() || rationale.isBlank()) {
            throw new IllegalArgumentException("slug and rationale must not be blank");
        }
        risks = risks == null ? List.of() : List.copyOf(risks);
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        bestWindow = blankToNull(bestWindow);
        if (sourceRefs.isEmpty()) {
            throw new IllegalArgumentException("sourceRefs must not be empty");
        }
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
