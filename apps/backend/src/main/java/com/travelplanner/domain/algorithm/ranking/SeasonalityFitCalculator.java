package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.WeatherBand;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** {@code w2·seasonality_fit(seasonality, brief.dates)}. */
final class SeasonalityFitCalculator {

    static final double NEUTRAL = 0.5d;

    private SeasonalityFitCalculator() {
    }

    /**
     * Soft score for available months. Empty optional means a hard exclusion
     * ({@link ExclusionReason#NO_SEASONALITY_FOR_DATES}).
     */
    static Optional<SeasonalitySignal> score(DestinationCandidate candidate, TripBriefDetails brief) {
        DateRange dates = brief.dates();
        if (dates == null) {
            return Optional.of(new SeasonalitySignal(NEUTRAL, 0.0d));
        }
        Set<Integer> monthsNeeded = monthsInRange(dates);
        Map<Integer, SeasonalityMonth> byMonth = indexByMonth(candidate);
        int found = 0;
        double sum = 0.0d;
        for (int month : monthsNeeded) {
            SeasonalityMonth row = byMonth.get(month);
            if (row == null) {
                continue;
            }
            found++;
            sum += monthScore(row);
        }
        if (found == 0) {
            return Optional.empty();
        }
        double missingFraction = 1.0d - (found / (double) monthsNeeded.size());
        return Optional.of(new SeasonalitySignal(sum / found, missingFraction));
    }

    private static Map<Integer, SeasonalityMonth> indexByMonth(DestinationCandidate candidate) {
        Map<Integer, SeasonalityMonth> byMonth = new HashMap<>();
        for (SeasonalityMonth month : candidate.seasonalityMonths()) {
            byMonth.putIfAbsent(month.month(), month);
        }
        return byMonth;
    }

    private static Set<Integer> monthsInRange(DateRange dates) {
        Set<Integer> months = new HashSet<>();
        LocalDate cursor = dates.start();
        LocalDate end = dates.end();
        while (!cursor.isAfter(end)) {
            months.add(cursor.getMonthValue());
            cursor = cursor.plusDays(1);
            // Cap pathological ranges: twelve unique months is enough for recurring seasonality.
            if (months.size() == 12) {
                break;
            }
        }
        return months;
    }

    private static double monthScore(SeasonalityMonth month) {
        return (weatherScore(month.weatherBand())
                + crowdScore(month.crowdBand())
                + priceBandScore(month.priceBand()))
                / 3.0d;
    }

    private static double weatherScore(WeatherBand band) {
        return switch (band) {
            case MILD -> 1.0d;
            case WARM -> 0.9d;
            case COOL -> 0.7d;
            case HOT -> 0.55d;
            case COLD -> 0.35d;
            case WET -> 0.4d;
            case STORMY -> 0.2d;
        };
    }

    private static double crowdScore(CrowdBand band) {
        return switch (band) {
            case LOW -> 1.0d;
            case MODERATE -> 0.75d;
            case HIGH -> 0.4d;
            case PEAK -> 0.2d;
        };
    }

    private static double priceBandScore(PriceBand band) {
        return switch (band) {
            case FREE -> 1.0d;
            case BUDGET -> 0.9d;
            case MODERATE -> 0.7d;
            case EXPENSIVE -> 0.4d;
            case LUXURY -> 0.2d;
        };
    }

    record SeasonalitySignal(double fit, double missingMonthFraction) {
    }
}
