package com.travelplanner.domain.algorithm.ranking;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic destination ranking for C2 (PLAN §4.0.3, §4.1.2).
 *
 * <p>Pure: no Spring, no I/O, no LLM. Same {@link RankingInput} always yields the same
 * {@link RankingResult}. Persist {@link #ALGORITHM_VERSION} with recommendations later.
 *
 * @implNote Per candidate work is {@code O(m)} in seasonality/price rows (bounded); selection is
 *           {@code O(n log n)} — see {@link TopKSelector}.
 */
public final class DestinationRanker {

    /** Bump when weights defaults, hard filters, or term formulas change. */
    public static final String ALGORITHM_VERSION = "destination-ranker-v1";

    public DestinationRanker() {
    }

    public RankingResult topK(RankingInput input) {
        List<TopKSelector.ScoredCandidate> scored = new ArrayList<>();
        List<ExcludedDestination> excluded = new ArrayList<>();
        for (DestinationCandidate candidate : input.candidates()) {
            classify(candidate, input, scored, excluded);
        }
        List<RankedDestination> ranked = TopKSelector.select(scored, input.topK());
        return new RankingResult(ALGORITHM_VERSION, ranked, excluded, ranked.isEmpty());
    }

    private static void classify(
            DestinationCandidate candidate,
            RankingInput input,
            List<TopKSelector.ScoredCandidate> scored,
            List<ExcludedDestination> excluded) {
        DestinationScorer.ScoreAttempt attempt = DestinationScorer.score(
                candidate, input.brief(), input.weights(), input.asOf());
        if (attempt.excluded()) {
            excluded.add(new ExcludedDestination(
                    candidate.destinationId(),
                    candidate.slug(),
                    attempt.exclusion(),
                    attempt.breakdown()));
            return;
        }
        ScoreBreakdown breakdown = attempt.breakdown();
        if (breakdown.confidence() < input.minConfidence()) {
            excluded.add(new ExcludedDestination(
                    candidate.destinationId(),
                    candidate.slug(),
                    ExclusionReason.LOW_CONFIDENCE,
                    breakdown));
            return;
        }
        scored.add(new TopKSelector.ScoredCandidate(candidate, breakdown));
    }
}
