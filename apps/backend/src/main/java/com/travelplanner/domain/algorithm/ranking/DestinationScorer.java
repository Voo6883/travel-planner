package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.model.TripBriefDetails;
import java.time.Instant;
import java.util.Optional;

/** Scores one candidate or returns a hard-filter exclusion. */
final class DestinationScorer {

    private DestinationScorer() {
    }

    static ScoreAttempt score(
            DestinationCandidate candidate,
            TripBriefDetails brief,
            ScoringWeights weights,
            Instant asOf) {
        if (!candidate.coverageLevel().isRankingEligible()) {
            return ScoreAttempt.excluded(ExclusionReason.NOT_RANKING_ELIGIBLE);
        }
        Optional<SeasonalityFitCalculator.SeasonalitySignal> seasonality =
                SeasonalityFitCalculator.score(candidate, brief);
        if (seasonality.isEmpty()) {
            return ScoreAttempt.excluded(ExclusionReason.NO_SEASONALITY_FOR_DATES);
        }
        PriceFitCalculator.PriceSignal price = PriceFitCalculator.evaluate(candidate, brief);
        if (price.outcome() == PriceFitCalculator.PriceOutcome.CURRENCY_MISMATCH) {
            return ScoreAttempt.excluded(ExclusionReason.CURRENCY_MISMATCH);
        }
        if (price.outcome() == PriceFitCalculator.PriceOutcome.OVER_BUDGET) {
            return ScoreAttempt.excluded(ExclusionReason.BUDGET_EXCEEDED, price.estimatedCost());
        }
        return ScoreAttempt.scored(buildBreakdown(candidate, brief, weights, asOf, seasonality.get(),
                price));
    }

    private static ScoreBreakdown buildBreakdown(
            DestinationCandidate candidate,
            TripBriefDetails brief,
            ScoringWeights weights,
            Instant asOf,
            SeasonalityFitCalculator.SeasonalitySignal seasonality,
            PriceFitCalculator.PriceSignal price) {
        var signals = new ScoreBreakdown.SignalScores(
                InterestMatchCalculator.score(candidate, brief),
                seasonality.fit(),
                price.fit(),
                AreaCoverageCalculator.score(candidate, brief));
        double freshness = FreshnessCalculator.factor(candidate, asOf);
        double confidence = ConfidenceCalculator.score(brief, seasonality, price);
        return ScoreBreakdown.of(signals, weights, freshness, confidence, price.estimatedCost());
    }

    record ScoreAttempt(ExclusionReason exclusion, ScoreBreakdown breakdown) {

        static ScoreAttempt excluded(ExclusionReason reason) {
            return new ScoreAttempt(reason, null);
        }

        static ScoreAttempt excluded(ExclusionReason reason, com.travelplanner.domain.valueobject.Money cost) {
            // Preserve estimate on budget exclusions when present — UI can show "over by X".
            ScoreBreakdown partial = cost == null
                    ? null
                    : new ScoreBreakdown(0, 0, 0, 0, 1, 0, 0, cost);
            return new ScoreAttempt(reason, partial);
        }

        static ScoreAttempt scored(ScoreBreakdown breakdown) {
            return new ScoreAttempt(null, breakdown);
        }

        boolean excluded() {
            return exclusion != null;
        }
    }
}
