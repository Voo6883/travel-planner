package com.travelplanner.domain.algorithm.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stable top-K selection.
 *
 * @implNote {@code O(n log n)} sort then limit — fine for curated destination sets (≪ 500). A
 *           heap would be {@code O(n log k)} if measured need appears.
 */
final class TopKSelector {

    private TopKSelector() {
    }

    static final Comparator<ScoredCandidate> ORDER = Comparator
            .comparingDouble((ScoredCandidate scored) -> scored.breakdown().fitScore())
            .reversed()
            .thenComparing(Comparator
                    .comparingDouble((ScoredCandidate scored) -> scored.breakdown().confidence())
                    .reversed())
            .thenComparing(scored -> scored.candidate().slug())
            .thenComparing(scored -> scored.candidate().destinationId());

    static List<RankedDestination> select(List<ScoredCandidate> scored, int topK) {
        List<ScoredCandidate> ordered = new ArrayList<>(scored);
        ordered.sort(ORDER);
        int limit = Math.min(topK, ordered.size());
        List<RankedDestination> ranked = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            ScoredCandidate item = ordered.get(index);
            ranked.add(new RankedDestination(
                    item.candidate().destinationId(),
                    item.candidate().slug(),
                    item.candidate().countryCode(),
                    index + 1,
                    item.breakdown()));
        }
        return List.copyOf(ranked);
    }

    record ScoredCandidate(DestinationCandidate candidate, ScoreBreakdown breakdown) {
    }
}
