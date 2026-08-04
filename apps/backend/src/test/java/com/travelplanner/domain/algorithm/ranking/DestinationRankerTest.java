package com.travelplanner.domain.algorithm.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DestinationRankerTest {

    private final DestinationRanker ranker = new DestinationRanker();

    @Test
    void emptyCandidatesYieldTypedNoConfidentResult() {
        RankingResult result = ranker.topK(input(List.of(), completeBrief(), 5));

        assertThat(result.noConfidentResult()).isTrue();
        assertThat(result.ranked()).isEmpty();
        assertThat(result.excluded()).isEmpty();
        assertThat(result.algorithmVersion()).isEqualTo(DestinationRanker.ALGORITHM_VERSION);
    }

    @Test
    void singleEligibleCandidateRanksFirst() {
        DestinationCandidate tokyo = RankingFixtures.fullCity(
                "tokyo-jp", "JP", RankingFixtures.allInterestCategories());

        RankingResult result = ranker.topK(input(List.of(tokyo), completeBrief(), 3));

        assertThat(result.noConfidentResult()).isFalse();
        assertThat(result.ranked()).hasSize(1);
        assertThat(result.ranked().getFirst().rank()).isEqualTo(1);
        assertThat(result.ranked().getFirst().slug()).isEqualTo("tokyo-jp");
        assertThat(result.ranked().getFirst().breakdown().fitScore()).isGreaterThan(0.0d);
    }

    @Test
    void tiesBreakBySlugThenId() {
        Set<PoiCategory> categories = RankingFixtures.allInterestCategories();
        DestinationCandidate alpha = RankingFixtures.fullCity("alpha-xx", "XX", categories);
        DestinationCandidate beta = RankingFixtures.fullCity("beta-xx", "XX", categories);

        RankingResult result = ranker.topK(input(List.of(beta, alpha), completeBrief(), 5));

        assertThat(result.ranked()).extracting(RankedDestination::slug)
                .containsExactly("alpha-xx", "beta-xx");
        assertThat(result.ranked().get(0).breakdown().fitScore())
                .isCloseTo(result.ranked().get(1).breakdown().fitScore(), within(1e-9));
    }

    @Test
    void topKLimitsResults() {
        Set<PoiCategory> categories = RankingFixtures.allInterestCategories();
        List<DestinationCandidate> cities = List.of(
                RankingFixtures.fullCity("a-city", "AA", categories),
                RankingFixtures.fullCity("b-city", "BB", categories),
                RankingFixtures.fullCity("c-city", "CC", categories));

        RankingResult result = ranker.topK(input(cities, completeBrief(), 2));

        assertThat(result.ranked()).hasSize(2);
        assertThat(result.ranked()).extracting(RankedDestination::rank).containsExactly(1, 2);
    }

    @Test
    void sameInputsAlwaysProduceSameRanking() {
        List<DestinationCandidate> cities = countryFixtures();
        RankingInput rankingInput = input(cities, completeBrief(), 3);

        RankingResult first = ranker.topK(rankingInput);
        RankingResult second = ranker.topK(rankingInput);

        assertThat(second).isEqualTo(first);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hardFilterCases")
    void hardFiltersExcludeWithTypedReason(
            String name,
            DestinationCandidate candidate,
            TripBriefDetails brief,
            ExclusionReason expected) {
        RankingResult result = ranker.topK(input(List.of(candidate), brief, 5));

        assertThat(result.noConfidentResult()).isTrue();
        assertThat(result.ranked()).isEmpty();
        assertThat(result.excluded()).hasSize(1);
        assertThat(result.excluded().getFirst().reason()).isEqualTo(expected);
    }

    static Stream<Arguments> hardFilterCases() {
        UUID id = UUID.nameUUIDFromBytes("partial-city".getBytes());
        DestinationCandidate partial = RankingFixtures.candidate(
                "partial-city",
                "XX",
                CoverageLevel.PARTIAL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(id),
                RankingFixtures.samplePrices(id, "USD", "120.00", "15.00", "5.00"));

        UUID cheapId = UUID.nameUUIDFromBytes("pricey-city".getBytes());
        DestinationCandidate pricey = RankingFixtures.candidate(
                "pricey-city",
                "XX",
                CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(cheapId),
                RankingFixtures.samplePrices(cheapId, "USD", "500.00", "40.00", "20.00"));

        UUID bareId = UUID.nameUUIDFromBytes("bare-city".getBytes());
        DestinationCandidate noSeasonality = RankingFixtures.candidate(
                "bare-city",
                "XX",
                CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                List.of(),
                RankingFixtures.samplePrices(bareId, "USD", "120.00", "15.00", "5.00"));

        UUID yenId = UUID.nameUUIDFromBytes("yen-city".getBytes());
        DestinationCandidate yenPrices = RankingFixtures.candidate(
                "yen-city",
                "JP",
                CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(yenId),
                RankingFixtures.samplePrices(yenId, "JPY", "15000", "1500", "800"));

        TripBriefDetails complete = completeBrief();
        TripBriefDetails tightBudget = RankingFixtures.brief(
                List.of(TravelInterest.FOOD, TravelInterest.SIGHTSEEING),
                RankingFixtures.julyWeek(),
                Money.of("200.00", "USD"),
                PartySize.ofAdults(2),
                TravelPace.MODERATE);

        return Stream.of(
                Arguments.of("partial coverage", partial, complete, ExclusionReason.NOT_RANKING_ELIGIBLE),
                Arguments.of("over budget", pricey, tightBudget, ExclusionReason.BUDGET_EXCEEDED),
                Arguments.of("no seasonality", noSeasonality, complete,
                        ExclusionReason.NO_SEASONALITY_FOR_DATES),
                Arguments.of("currency mismatch", yenPrices, complete, ExclusionReason.CURRENCY_MISMATCH));
    }

    @Test
    void missingSignalsUseNeutralTermsAndStillRank() {
        DestinationCandidate tokyo = RankingFixtures.fullCity(
                "tokyo-jp", "JP", RankingFixtures.allInterestCategories());
        TripBriefDetails sparse = TripBriefDetails.empty();

        RankingResult result = ranker.topK(input(List.of(tokyo), sparse, 3));

        assertThat(result.ranked()).hasSize(1);
        ScoreBreakdown breakdown = result.ranked().getFirst().breakdown();
        assertThat(breakdown.interestMatch()).isEqualTo(InterestMatchCalculator.NEUTRAL);
        assertThat(breakdown.seasonalityFit()).isEqualTo(SeasonalityFitCalculator.NEUTRAL);
        assertThat(breakdown.priceFit()).isEqualTo(PriceFitCalculator.NEUTRAL);
        assertThat(breakdown.confidence()).isLessThan(1.0d);
    }

    @Test
    void stalePricingReducesFreshnessFactor() {
        UUID id = UUID.nameUUIDFromBytes("stale-city".getBytes());
        DestinationCandidate fresh = RankingFixtures.fullCity(
                "fresh-city", "XX", RankingFixtures.allInterestCategories());
        DestinationCandidate stale = RankingFixtures.candidate(
                "stale-city",
                "XX",
                CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(id),
                RankingFixtures.stalePrices(id));

        RankingResult result = ranker.topK(input(List.of(stale, fresh), completeBrief(), 5));

        ScoreBreakdown staleBreakdown = result.ranked().stream()
                .filter(row -> row.slug().equals("stale-city"))
                .findFirst()
                .orElseThrow()
                .breakdown();
        ScoreBreakdown freshBreakdown = result.ranked().stream()
                .filter(row -> row.slug().equals("fresh-city"))
                .findFirst()
                .orElseThrow()
                .breakdown();
        assertThat(staleBreakdown.freshnessFactor()).isLessThan(freshBreakdown.freshnessFactor());
        assertThat(freshBreakdown.freshnessFactor()).isEqualTo(1.0d);
    }

    @Test
    void interestMatchPrefersCoveringDestination() {
        DestinationCandidate foodCity = RankingFixtures.fullCity(
                "food-city", "XX", EnumSet.of(PoiCategory.FOOD));
        DestinationCandidate sightCity = RankingFixtures.fullCity(
                "sight-city", "XX", EnumSet.of(PoiCategory.SIGHT));
        TripBriefDetails foodBrief = RankingFixtures.brief(
                List.of(TravelInterest.FOOD),
                RankingFixtures.julyWeek(),
                Money.of("5000.00", "USD"),
                PartySize.ofAdults(2),
                TravelPace.MODERATE);

        RankingResult result = ranker.topK(input(List.of(sightCity, foodCity), foodBrief, 5));

        assertThat(result.ranked().getFirst().slug()).isEqualTo("food-city");
        assertThat(result.ranked().getFirst().breakdown().interestMatch()).isEqualTo(1.0d);
        assertThat(result.ranked().get(1).breakdown().interestMatch()).isEqualTo(0.0d);
    }

    @Test
    void countryFixturesRankDeterministically() {
        RankingResult result = ranker.topK(input(countryFixtures(), completeBrief(), 3));

        assertThat(result.ranked()).hasSize(3);
        assertThat(result.ranked()).extracting(RankedDestination::slug)
                .containsExactly("bangkok-th", "shanghai-cn", "tokyo-jp");
        assertThat(result.excluded()).isEmpty();
    }

    @Test
    void peakSeasonScoresLowerThanMild() {
        UUID mildId = UUID.nameUUIDFromBytes("mild-city".getBytes());
        UUID peakId = UUID.nameUUIDFromBytes("peak-city".getBytes());
        DestinationCandidate mild = RankingFixtures.candidate(
                "mild-city",
                "XX",
                CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(mildId),
                RankingFixtures.samplePrices(mildId, "USD", "120.00", "15.00", "5.00"));
        DestinationCandidate peak = RankingFixtures.candidate(
                "peak-city",
                "XX",
                CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.peakSummer(peakId),
                RankingFixtures.samplePrices(peakId, "USD", "120.00", "15.00", "5.00"));

        RankingResult result = ranker.topK(input(List.of(peak, mild), completeBrief(), 5));

        assertThat(result.ranked().getFirst().slug()).isEqualTo("mild-city");
        assertThat(result.ranked().getFirst().breakdown().seasonalityFit())
                .isGreaterThan(result.ranked().get(1).breakdown().seasonalityFit());
    }

    @Test
    void packedPaceDemandsMoreAreas() {
        UUID smallId = UUID.nameUUIDFromBytes("small-city".getBytes());
        DestinationCandidate small = RankingFixtures.candidate(
                "small-city",
                "XX",
                CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                1,
                RankingFixtures.twelveMildMonths(smallId),
                RankingFixtures.samplePrices(smallId, "USD", "120.00", "15.00", "5.00"));
        TripBriefDetails packed = RankingFixtures.brief(
                List.of(TravelInterest.FOOD),
                RankingFixtures.julyWeek(),
                Money.of("5000.00", "USD"),
                PartySize.ofAdults(4),
                TravelPace.PACKED);

        RankingResult result = ranker.topK(input(List.of(small), packed, 3));

        assertThat(result.ranked()).hasSize(1);
        assertThat(result.ranked().getFirst().breakdown().areaCoverage()).isLessThan(1.0d);
    }

    @Test
    void lowConfidenceIsTypedExclusion() {
        UUID id = UUID.nameUUIDFromBytes("sparse-city".getBytes());
        DestinationCandidate sparsePrices = RankingFixtures.candidate(
                "sparse-city",
                "XX",
                CoverageLevel.FULL,
                RankingFixtures.allInterestCategories(),
                4,
                RankingFixtures.twelveMildMonths(id),
                List.of());
        TripBriefDetails brief = RankingFixtures.brief(
                List.of(),
                RankingFixtures.julyWeek(),
                Money.of("5000.00", "USD"),
                null,
                null);
        RankingInput rankingInput = new RankingInput(
                List.of(sparsePrices),
                brief,
                ScoringWeights.defaults(),
                3,
                RankingFixtures.AS_OF,
                0.80d);

        RankingResult result = ranker.topK(rankingInput);

        assertThat(result.noConfidentResult()).isTrue();
        assertThat(result.excluded().getFirst().reason()).isEqualTo(ExclusionReason.LOW_CONFIDENCE);
        assertThat(result.excluded().getFirst().partialBreakdownIfPresent()).isPresent();
    }


    @Test
    void defaultWeightsArePositiveAndDocumented() {
        ScoringWeights weights = ScoringWeights.defaults();
        assertThat(weights.interestWeight()).isEqualTo(0.35d);
        assertThat(weights.total()).isCloseTo(1.0d, within(1e-9));
    }

    private static List<DestinationCandidate> countryFixtures() {
        Set<PoiCategory> categories = RankingFixtures.allInterestCategories();
        return List.of(
                RankingFixtures.fullCity("tokyo-jp", "JP", categories),
                RankingFixtures.fullCity("bangkok-th", "TH", categories),
                RankingFixtures.fullCity("shanghai-cn", "CN", categories));
    }

    private static TripBriefDetails completeBrief() {
        return RankingFixtures.brief(
                List.of(TravelInterest.FOOD, TravelInterest.SIGHTSEEING, TravelInterest.MUSEUMS),
                RankingFixtures.julyWeek(),
                Money.of("5000.00", "USD"),
                PartySize.ofAdults(2),
                TravelPace.MODERATE);
    }

    private static RankingInput input(
            List<DestinationCandidate> candidates,
            TripBriefDetails brief,
            int topK) {
        return new RankingInput(candidates, brief, ScoringWeights.defaults(), topK, RankingFixtures.AS_OF);
    }
}
