package com.travelplanner.domain.algorithm.ranking;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A candidate removed before or after scoring, with a typed reason. */
public record ExcludedDestination(
        UUID destinationId,
        String slug,
        ExclusionReason reason,
        ScoreBreakdown partialBreakdown) {

    public ExcludedDestination {
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(reason, "reason");
        if (slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
    }

    public static ExcludedDestination of(UUID id, String slug, ExclusionReason reason) {
        return new ExcludedDestination(id, slug, reason, null);
    }

    public Optional<ScoreBreakdown> partialBreakdownIfPresent() {
        return Optional.ofNullable(partialBreakdown);
    }
}
