package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.valueobject.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-term scores that later recommendations persist so UI/agent can explain fit (task 24 DoD).
 *
 * <p>Every term is in {@code [0, 1]}. {@link #fitScore()} is the weighted sum times
 * {@link #freshnessFactor()}.
 */
public record ScoreBreakdown(
        double interestMatch,
        double seasonalityFit,
        double priceFit,
        double areaCoverage,
        double freshnessFactor,
        double confidence,
        double fitScore,
        Money estimatedCost) {

    public ScoreBreakdown {
        interestMatch = clampUnit(interestMatch);
        seasonalityFit = clampUnit(seasonalityFit);
        priceFit = clampUnit(priceFit);
        areaCoverage = clampUnit(areaCoverage);
        freshnessFactor = clampUnit(freshnessFactor);
        confidence = clampUnit(confidence);
        if (Double.isNaN(fitScore) || fitScore < 0.0d) {
            throw new IllegalArgumentException("fitScore must be non-negative, got " + fitScore);
        }
    }

    public Optional<Money> estimatedCostIfPresent() {
        return Optional.ofNullable(estimatedCost);
    }

    private static double clampUnit(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("score terms must be numbers");
        }
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }

    /** Builds a breakdown and computes {@code fitScore} from weights. */
    static ScoreBreakdown of(
            SignalScores signals,
            ScoringWeights weights,
            double freshnessFactor,
            double confidence,
            Money estimatedCost) {
        Objects.requireNonNull(signals, "signals");
        Objects.requireNonNull(weights, "weights");
        double raw = signals.interestMatch() * weights.interestWeight()
                + signals.seasonalityFit() * weights.seasonalityWeight()
                + signals.priceFit() * weights.priceWeight()
                + signals.areaCoverage() * weights.areaWeight();
        double normalised = raw / weights.total();
        double fit = normalised * freshnessFactor;
        return new ScoreBreakdown(
                signals.interestMatch(),
                signals.seasonalityFit(),
                signals.priceFit(),
                signals.areaCoverage(),
                freshnessFactor,
                confidence,
                fit,
                estimatedCost);
    }

    record SignalScores(
            double interestMatch,
            double seasonalityFit,
            double priceFit,
            double areaCoverage) {
    }
}
