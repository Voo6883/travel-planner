package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.enums.KnowledgeDataClass;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.SeasonalityMonth;
import java.time.Instant;

/** Freshness multiplier from seasonality / price provenance TTLs (ADR 010 §6). */
final class FreshnessCalculator {

    /** Maximum fraction subtracted from 1.0 when every pricing fact is stale. */
    static final double MAX_PENALTY = 0.30d;

    private FreshnessCalculator() {
    }

    static double factor(DestinationCandidate candidate, Instant asOf) {
        int total = 0;
        int stale = 0;
        for (SeasonalityMonth month : candidate.seasonalityMonths()) {
            total++;
            if (month.provenance().isStaleAt(asOf, KnowledgeDataClass.SEASONAL_PRICING)) {
                stale++;
            }
        }
        for (PriceObservation observation : candidate.priceObservations()) {
            total++;
            if (observation.provenance().isStaleAt(asOf, KnowledgeDataClass.SEASONAL_PRICING)) {
                stale++;
            }
        }
        if (total == 0) {
            return 1.0d;
        }
        return 1.0d - MAX_PENALTY * (stale / (double) total);
    }
}
