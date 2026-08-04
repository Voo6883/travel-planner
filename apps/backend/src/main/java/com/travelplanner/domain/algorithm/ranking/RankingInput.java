package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.model.TripBriefDetails;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * All inputs to {@link DestinationRanker#topK(RankingInput)} — keeps the public API at one
 * parameter (AGENTS ≤3-params rule).
 */
public record RankingInput(
        List<DestinationCandidate> candidates,
        TripBriefDetails brief,
        ScoringWeights weights,
        int topK,
        Instant asOf,
        double minConfidence) {

    /** Confidence below this after scoring is excluded as {@link ExclusionReason#LOW_CONFIDENCE}. */
    public static final double DEFAULT_MIN_CONFIDENCE = 0.45d;

    public RankingInput(
            List<DestinationCandidate> candidates,
            TripBriefDetails brief,
            ScoringWeights weights,
            int topK,
            Instant asOf) {
        this(candidates, brief, weights, topK, asOf, DEFAULT_MIN_CONFIDENCE);
    }

    public RankingInput {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(brief, "brief");
        Objects.requireNonNull(weights, "weights");
        Objects.requireNonNull(asOf, "asOf");
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1, got " + topK);
        }
        if (Double.isNaN(minConfidence) || minConfidence < 0.0d || minConfidence > 1.0d) {
            throw new IllegalArgumentException(
                    "minConfidence must be in [0, 1], got " + minConfidence);
        }
    }
}
