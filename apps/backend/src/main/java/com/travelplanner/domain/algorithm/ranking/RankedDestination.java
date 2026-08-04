package com.travelplanner.domain.algorithm.ranking;

import java.util.Objects;
import java.util.UUID;

/** One destination that survived hard filters and the confidence floor. */
public record RankedDestination(
        UUID destinationId,
        String slug,
        String countryCode,
        int rank,
        ScoreBreakdown breakdown) {

    public RankedDestination {
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(breakdown, "breakdown");
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be at least 1, got " + rank);
        }
        if (slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
    }
}
