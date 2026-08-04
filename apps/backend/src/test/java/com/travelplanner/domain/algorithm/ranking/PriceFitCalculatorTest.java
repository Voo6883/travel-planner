package com.travelplanner.domain.algorithm.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriceFitCalculatorTest {

    @Test
    void estimatesTripCostFromCanonicalCategories() {
        UUID id = UUID.nameUUIDFromBytes("cost-city".getBytes());
        DestinationCandidate candidate = RankingFixtures.candidate(
                "cost-city",
                "XX",
                com.travelplanner.domain.enums.CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(id),
                RankingFixtures.samplePrices(id, "USD", "100.00", "10.00", "5.00"));
        TripBriefDetails brief = RankingFixtures.brief(
                List.of(TravelInterest.FOOD),
                DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)),
                Money.of("5000.00", "USD"),
                PartySize.ofAdults(2),
                TravelPace.MODERATE);

        PriceFitCalculator.PriceSignal signal = PriceFitCalculator.evaluate(candidate, brief);

        // nights=2, rooms=1 → hotel 200; days=3, meals 2*2*3*10=120; transit 3*2*5=30 → 350
        assertThat(signal.outcome()).isEqualTo(PriceFitCalculator.PriceOutcome.OK);
        assertThat(signal.estimatedCost()).isEqualTo(Money.of("350.00", "USD"));
        assertThat(signal.fit()).isGreaterThan(0.25d);
    }

    @Test
    void boundaryExactBudgetStillFits() {
        UUID id = UUID.nameUUIDFromBytes("exact-city".getBytes());
        DestinationCandidate candidate = RankingFixtures.candidate(
                "exact-city",
                "XX",
                com.travelplanner.domain.enums.CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(id),
                RankingFixtures.samplePrices(id, "USD", "100.00", "10.00", "5.00"));
        // cost = 350 as above
        TripBriefDetails brief = RankingFixtures.brief(
                List.of(TravelInterest.FOOD),
                DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)),
                Money.of("350.00", "USD"),
                PartySize.ofAdults(2),
                TravelPace.MODERATE);

        PriceFitCalculator.PriceSignal signal = PriceFitCalculator.evaluate(candidate, brief);

        assertThat(signal.outcome()).isEqualTo(PriceFitCalculator.PriceOutcome.OK);
        assertThat(signal.fit()).isCloseTo(0.25d, within(1e-9));
    }

    @Test
    void oneDollarOverBudgetIsExcludedOutcome() {
        UUID id = UUID.nameUUIDFromBytes("over-city".getBytes());
        DestinationCandidate candidate = RankingFixtures.candidate(
                "over-city",
                "XX",
                com.travelplanner.domain.enums.CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(id),
                RankingFixtures.samplePrices(id, "USD", "100.00", "10.00", "5.00"));
        TripBriefDetails brief = RankingFixtures.brief(
                List.of(TravelInterest.FOOD),
                DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3)),
                Money.of("349.00", "USD"),
                PartySize.ofAdults(2),
                TravelPace.MODERATE);

        assertThat(PriceFitCalculator.evaluate(candidate, brief).outcome())
                .isEqualTo(PriceFitCalculator.PriceOutcome.OVER_BUDGET);
    }

    @Test
    void scoringWeightsRejectNegative() {
        assertThatThrownBy(() -> new ScoringWeights(-0.1d, 0.5d, 0.5d, 0.1d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
