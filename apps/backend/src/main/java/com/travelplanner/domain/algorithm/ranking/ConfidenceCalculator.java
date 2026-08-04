package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.model.TripBriefDetails;

/** Confidence in {@code [0, 1]} from how complete the brief and candidate signals are. */
final class ConfidenceCalculator {

    private ConfidenceCalculator() {
    }

    static double score(
            TripBriefDetails brief,
            SeasonalityFitCalculator.SeasonalitySignal seasonality,
            PriceFitCalculator.PriceSignal price) {
        double confidence = 1.0d;
        if (brief.interests().isEmpty()) {
            confidence -= 0.05d;
        }
        if (brief.dates() == null) {
            confidence -= 0.10d;
        } else {
            confidence -= 0.10d * seasonality.missingMonthFraction();
        }
        if (brief.budget() == null) {
            confidence -= 0.10d;
        }
        if (price.outcome() == PriceFitCalculator.PriceOutcome.MISSING_DATA) {
            confidence -= 0.25d;
        }
        if (brief.party() == null) {
            confidence -= 0.05d;
        }
        if (brief.pace() == null) {
            confidence -= 0.05d;
        }
        return Math.max(0.0d, confidence);
    }
}
