package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.WeatherBand;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared builders for ranking table tests. */
final class RankingFixtures {

    static final Instant AS_OF = Instant.parse("2026-06-01T00:00:00Z");

    private RankingFixtures() {
    }

    static TripBriefDetails brief(
            List<TravelInterest> interests,
            DateRange dates,
            Money budget,
            PartySize party,
            TravelPace pace) {
        return TripBriefDetails.empty()
                .withInterests(interests)
                .withDates(dates)
                .withBudget(budget)
                .withParty(party)
                .withPace(pace);
    }

    static DestinationCandidate candidate(
            String slug,
            String country,
            CoverageLevel coverage,
            Set<PoiCategory> categories,
            int areaCount,
            List<SeasonalityMonth> seasonality,
            List<PriceObservation> prices) {
        UUID id = UUID.nameUUIDFromBytes(slug.getBytes());
        return new DestinationCandidate(
                id, slug, country, coverage, categories, seasonality, prices, areaCount);
    }

    static DestinationCandidate fullCity(String slug, String country, Set<PoiCategory> categories) {
        UUID destinationId = UUID.nameUUIDFromBytes(slug.getBytes());
        return candidate(
                slug,
                country,
                CoverageLevel.FULL,
                categories,
                4,
                twelveMildMonths(destinationId),
                samplePrices(destinationId, "USD", "120.00", "15.00", "5.00"));
    }

    static List<SeasonalityMonth> twelveMildMonths(UUID destinationId) {
        List<SeasonalityMonth> months = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            months.add(new SeasonalityMonth(
                    UUID.nameUUIDFromBytes((destinationId + "-m" + month).getBytes()),
                    destinationId,
                    month,
                    WeatherBand.MILD,
                    CrowdBand.MODERATE,
                    PriceBand.MODERATE,
                    null,
                    KnowledgeFixtures.provenance()));
        }
        return months;
    }

    static List<SeasonalityMonth> peakSummer(UUID destinationId) {
        List<SeasonalityMonth> months = twelveMildMonths(destinationId);
        months.set(6, new SeasonalityMonth(
                UUID.nameUUIDFromBytes((destinationId + "-m7").getBytes()),
                destinationId,
                7,
                WeatherBand.HOT,
                CrowdBand.PEAK,
                PriceBand.EXPENSIVE,
                "peak",
                KnowledgeFixtures.provenance()));
        return months;
    }

    static List<PriceObservation> samplePrices(
            UUID destinationId,
            String currency,
            String hotel,
            String meal,
            String transit) {
        KnowledgeProvenance provenance = KnowledgeFixtures.provenance();
        LocalDate observed = LocalDate.of(2026, 1, 1);
        return List.of(
                price(destinationId, PriceFitCalculator.HOTEL_NIGHT, hotel, currency, observed,
                        provenance),
                price(destinationId, PriceFitCalculator.MEAL_MID_RANGE, meal, currency, observed,
                        provenance),
                price(destinationId, PriceFitCalculator.TRANSIT_DAY_PASS, transit, currency, observed,
                        provenance));
    }

    static List<PriceObservation> stalePrices(UUID destinationId) {
        KnowledgeProvenance stale = new KnowledgeProvenance(
                KnowledgeFixtures.provenance().sourceRef(),
                KnowledgeFixtures.provenance().name(),
                KnowledgeFixtures.provenance().licence(),
                KnowledgeFixtures.provenance().attributionText(),
                KnowledgeFixtures.provenance().sourceUrl(),
                KnowledgeFixtures.provenance().trustTier(),
                Instant.parse("2020-01-01T00:00:00Z"));
        LocalDate observed = LocalDate.of(2020, 1, 1);
        return List.of(
                price(destinationId, PriceFitCalculator.HOTEL_NIGHT, "120.00", "USD", observed, stale),
                price(destinationId, PriceFitCalculator.MEAL_MID_RANGE, "15.00", "USD", observed, stale),
                price(destinationId, PriceFitCalculator.TRANSIT_DAY_PASS, "5.00", "USD", observed,
                        stale));
    }

    static PriceObservation price(
            UUID destinationId,
            String category,
            String amount,
            String currency,
            LocalDate observedOn,
            KnowledgeProvenance provenance) {
        return new PriceObservation(
                UUID.nameUUIDFromBytes((destinationId + category + observedOn).getBytes()),
                destinationId,
                category,
                Money.of(amount, currency),
                observedOn,
                provenance);
    }

    static Set<PoiCategory> allInterestCategories() {
        return EnumSet.of(
                PoiCategory.FOOD,
                PoiCategory.SIGHT,
                PoiCategory.MUSEUM,
                PoiCategory.NATURE,
                PoiCategory.SHOPPING,
                PoiCategory.NIGHTLIFE,
                PoiCategory.EXPERIENCE);
    }

    static DateRange julyWeek() {
        return DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7));
    }
}
