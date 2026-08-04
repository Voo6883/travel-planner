package com.travelplanner.domain.algorithm.ranking;

/**
 * Relative weights for the four C2 fit terms (PLAN §4.1.2).
 *
 * <p>Defaults emphasise interest match (UC-C2-11) while keeping seasonality and price material.
 * Callers may substitute a configured set; the algorithm never invents weights at score time.
 */
public record ScoringWeights(
        double interestWeight,
        double seasonalityWeight,
        double priceWeight,
        double areaWeight) {

    /** Interests 0.35 · seasonality 0.25 · price 0.25 · area 0.15. */
    public static ScoringWeights defaults() {
        return new ScoringWeights(0.35d, 0.25d, 0.25d, 0.15d);
    }

    public ScoringWeights {
        requireNonNegative("interestWeight", interestWeight);
        requireNonNegative("seasonalityWeight", seasonalityWeight);
        requireNonNegative("priceWeight", priceWeight);
        requireNonNegative("areaWeight", areaWeight);
        double sum = interestWeight + seasonalityWeight + priceWeight + areaWeight;
        if (sum <= 0.0d) {
            throw new IllegalArgumentException("scoring weights must sum to a positive total");
        }
    }

    public double total() {
        return interestWeight + seasonalityWeight + priceWeight + areaWeight;
    }

    private static void requireNonNegative(String name, double value) {
        if (Double.isNaN(value) || value < 0.0d) {
            throw new IllegalArgumentException(name + " must be a non-negative number, got " + value);
        }
    }
}
