package com.travelplanner.application.research;

import com.travelplanner.domain.algorithm.ranking.DestinationCandidate;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.port.KnowledgePort;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.travelplanner.config.RequiresDatabase;

/**
 * Projects KnowledgePort rows into bounded {@link DestinationCandidate}s for the DSA (task 24/25).
 *
 * <p>Only {@link CoverageLevel#FULL} destinations are projected. Preferred brief destination slugs
 * are loaded first when present; otherwise the supported FULL set is used (surprise-me / open
 * preference). Unbounded POI dumps are never passed to the ranker — only the category set and
 * counts the DSA needs.
 */
@Component
@RequiresDatabase
public class DestinationCandidateBuilder {

    private static final List<String> PRICE_CATEGORIES =
            List.of("HOTEL_NIGHT", "MEAL_MID_RANGE", "TRANSIT_DAY_PASS");

    private final KnowledgePort knowledge;

    public DestinationCandidateBuilder(KnowledgePort knowledge) {
        this.knowledge = knowledge;
    }

    public List<DestinationCandidate> build(TripBriefDetails brief) {
        List<Destination> destinations = resolveDestinations(brief);
        List<DestinationCandidate> candidates = new ArrayList<>();
        for (Destination destination : destinations) {
            if (destination.coverageLevel() != CoverageLevel.FULL) {
                continue;
            }
            candidates.add(project(destination));
        }
        return List.copyOf(candidates);
    }

    private List<Destination> resolveDestinations(TripBriefDetails brief) {
        if (!brief.destinations().isEmpty()) {
            List<Destination> preferred = new ArrayList<>();
            for (String slug : brief.destinations()) {
                knowledge.findDestinationBySlug(slug.toLowerCase(Locale.ROOT))
                        .ifPresent(preferred::add);
            }
            if (!preferred.isEmpty()) {
                return preferred;
            }
        }
        return knowledge.findSupportedDestinations().stream()
                .filter(d -> d.coverageLevel() == CoverageLevel.FULL)
                .toList();
    }

    private DestinationCandidate project(Destination destination) {
        UUID id = destination.id();
        Set<PoiCategory> categories = EnumSet.noneOf(PoiCategory.class);
        for (Poi poi : knowledge.findPois(id, Optional.empty())) {
            categories.add(poi.category());
        }
        List<SeasonalityMonth> seasonality = knowledge.findSeasonality(id);
        List<PriceObservation> prices = new ArrayList<>();
        for (String category : PRICE_CATEGORIES) {
            prices.addAll(knowledge.findPriceHistory(id, category));
        }
        int areaCount = knowledge.findAreas(id).size();
        return new DestinationCandidate(
                id,
                destination.slug(),
                destination.countryCode(),
                destination.coverageLevel(),
                categories,
                seasonality,
                prices,
                areaCount);
    }
}
