package com.travelplanner.domain.algorithm.ranking;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic ranking outcome. {@link #noConfidentResult()} is the typed empty path (UC-C2-05).
 */
public record RankingResult(
        String algorithmVersion,
        List<RankedDestination> ranked,
        List<ExcludedDestination> excluded,
        boolean noConfidentResult) {

    public RankingResult {
        Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        ranked = List.copyOf(Objects.requireNonNull(ranked, "ranked"));
        excluded = List.copyOf(Objects.requireNonNull(excluded, "excluded"));
        if (algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion must not be blank");
        }
        if (noConfidentResult != ranked.isEmpty()) {
            throw new IllegalArgumentException(
                    "noConfidentResult must equal ranked.isEmpty()");
        }
    }

    public boolean isEmpty() {
        return ranked.isEmpty();
    }
}
