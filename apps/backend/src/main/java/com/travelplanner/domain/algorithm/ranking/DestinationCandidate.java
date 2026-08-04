package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.SeasonalityMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pre-aggregated, bounded signals for one destination (PLAN §4.0.3 typed I/O).
 *
 * <p>Built by the application layer from {@code KnowledgePort} results. The ranker never loads
 * data and never accepts an unbounded POI dump — callers must prefilter.
 */
public record DestinationCandidate(
        UUID destinationId,
        String slug,
        String countryCode,
        CoverageLevel coverageLevel,
        Set<PoiCategory> poiCategories,
        List<SeasonalityMonth> seasonalityMonths,
        List<PriceObservation> priceObservations,
        int areaCount) {

    public DestinationCandidate {
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(coverageLevel, "coverageLevel");
        poiCategories = Set.copyOf(Objects.requireNonNull(poiCategories, "poiCategories"));
        seasonalityMonths = List.copyOf(Objects.requireNonNull(seasonalityMonths, "seasonalityMonths"));
        priceObservations = List.copyOf(Objects.requireNonNull(priceObservations, "priceObservations"));
        if (slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
        if (areaCount < 0) {
            throw new IllegalArgumentException("areaCount must not be negative, got " + areaCount);
        }
    }
}
