package com.travelplanner.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.ai.tool.KnowledgeResearchTools;
import com.travelplanner.ai.tool.ResearchToolJson;
import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.algorithm.ranking.DestinationCandidate;
import com.travelplanner.domain.algorithm.ranking.DestinationRanker;
import com.travelplanner.domain.algorithm.ranking.RankingInput;
import com.travelplanner.domain.algorithm.ranking.RankingResult;
import com.travelplanner.domain.algorithm.ranking.ScoringWeights;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.WeatherBand;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.DestinationGuide;
import com.travelplanner.domain.model.DestinationNarrative;
import com.travelplanner.domain.model.KnowledgeMatch;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.model.TravelAppReplacement;
import com.travelplanner.domain.model.TravelResearchNarratives;
import com.travelplanner.domain.model.TravelResearchRequest;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.enums.TravelAppCategory;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Deterministic stub agent + guardrail fail-closed coverage for task 25. */
class StubTravelResearchAgentTest {

    private static final Instant AS_OF = Instant.parse("2026-06-01T00:00:00Z");

    private FakeKnowledge knowledge;
    private StubTravelResearchAgent agent;
    private DestinationRanker ranker;

    @BeforeEach
    void setUp() {
        knowledge = new FakeKnowledge();
        ResearchToolJson json = new ResearchToolJson(new ObjectMapper());
        agent = new StubTravelResearchAgent(new KnowledgeResearchTools(knowledge, json), json, 40, 24_000);
        ranker = new DestinationRanker();
    }

    @Test
    void stubAgentProducesGroundedNarrativesForFullCandidates() {
        Destination tokyo = knowledge.seedFull("tokyo-jp", "JP");
        TripBriefDetails brief = completeBrief();
        DestinationCandidate candidate = fullCandidate(tokyo);
        RankingResult ranking = ranker.topK(new RankingInput(
                List.of(candidate), brief, ScoringWeights.defaults(), 5, AS_OF));

        TravelResearchNarratives narratives = agent.research(
                new TravelResearchRequest(brief, List.of(candidate), ranking, pct -> { }));

        assertThat(ranking.noConfidentResult()).isFalse();
        assertThat(narratives.narratives()).hasSize(1);
        assertThat(narratives.modelName()).isEqualTo(StubTravelResearchAgent.MODEL_NAME);
        DestinationNarrative narrative = narratives.narratives().getFirst();
        assertThat(narrative.slug()).isEqualTo("tokyo-jp");
        assertThat(narrative.sourceRefs()).isNotEmpty();
        assertThat(narrative.sourceRefs())
                .extracting(RecommendationSourceRef::sourceRef)
                .contains(KnowledgeProvenance.SAMPLE_SOURCE_REF);
        assertThat(narrative.travelerGuide().overview()).contains("Tokyo");
    }

    @Test
    void noConfidentRankingYieldsEmptyNarrativesWithoutFailing() {
        RankingResult empty = new RankingResult(DestinationRanker.ALGORITHM_VERSION, List.of(),
                List.of(), true);
        TravelResearchNarratives narratives = agent.research(new TravelResearchRequest(
                completeBrief(), List.of(), empty, pct -> { }));
        assertThat(narratives.narratives()).isEmpty();
    }

    @Test
    void inventedSourceRefIsRejectedByGuardrails() {
        Destination tokyo = knowledge.seedFull("tokyo-jp", "JP");
        TripBriefDetails brief = completeBrief();
        DestinationCandidate candidate = fullCandidate(tokyo);
        RankingResult ranking = ranker.topK(new RankingInput(
                List.of(candidate), brief, ScoringWeights.defaults(), 5, AS_OF));
        TravelResearchNarratives ok = agent.research(
                new TravelResearchRequest(brief, List.of(candidate), ranking, pct -> { }));
        DestinationNarrative forged = new DestinationNarrative(
                ok.narratives().getFirst().destinationId(),
                "tokyo-jp",
                "forged",
                ok.narratives().getFirst().travelerGuide(),
                List.of(),
                null,
                List.of(RecommendationSourceRef.of("invented:ref", "overview")));
        assertThatThrownBy(() -> com.travelplanner.ai.guardrails.ResearchOutputGuardrails.validate(
                List.of(forged),
                Set.of(tokyo.id()),
                ledgerAllowing("tokyo-jp")))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void toolBudgetFailsClosedWhenExceeded() {
        Destination tokyo = knowledge.seedFull("tokyo-jp", "JP");
        StubTravelResearchAgent tiny = new StubTravelResearchAgent(
                new KnowledgeResearchTools(knowledge, new ResearchToolJson(new ObjectMapper())),
                new ResearchToolJson(new ObjectMapper()),
                2,
                24_000);
        DestinationCandidate candidate = fullCandidate(tokyo);
        RankingResult ranking = ranker.topK(new RankingInput(
                List.of(candidate), completeBrief(), ScoringWeights.defaults(), 5, AS_OF));
        assertThatThrownBy(() -> tiny.research(new TravelResearchRequest(
                completeBrief(), List.of(candidate), ranking, pct -> { })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max tool calls");
    }

    private static com.travelplanner.ai.tool.ResearchEvidenceLedger ledgerAllowing(String slug) {
        com.travelplanner.ai.tool.ResearchEvidenceLedger ledger =
                new com.travelplanner.ai.tool.ResearchEvidenceLedger();
        ledger.allowDestination(slug);
        ledger.noteSourceRef(KnowledgeProvenance.SAMPLE_SOURCE_REF);
        return ledger;
    }

    private static TripBriefDetails completeBrief() {
        return TripBriefDetails.empty()
                .withDates(new DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 8)))
                .withDateFlexibility(DateFlexibility.FIXED)
                .withDepartureCity("Singapore")
                .withBudget(Money.of("3000", "USD"))
                .withParty(PartySize.ofAdults(2))
                .withInterests(List.of(TravelInterest.FOOD, TravelInterest.SIGHTSEEING))
                .withPace(TravelPace.MODERATE);
    }

    static DestinationCandidate fullCandidate(Destination destination) {
        UUID id = destination.id();
        List<SeasonalityMonth> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            months.add(new SeasonalityMonth(
                    UUID.nameUUIDFromBytes((id + "-m" + month).getBytes()),
                    id, month, WeatherBand.MILD, CrowdBand.MODERATE, PriceBand.MODERATE, null,
                    KnowledgeFixtures.sampleProvenance()));
        }
        LocalDate observed = LocalDate.of(2026, 1, 1);
        List<PriceObservation> prices = List.of(
                new PriceObservation(UUID.randomUUID(), id, "HOTEL_NIGHT",
                        Money.of("120", "USD"), observed, KnowledgeFixtures.sampleProvenance()),
                new PriceObservation(UUID.randomUUID(), id, "MEAL_MID_RANGE",
                        Money.of("15", "USD"), observed, KnowledgeFixtures.sampleProvenance()),
                new PriceObservation(UUID.randomUUID(), id, "TRANSIT_DAY_PASS",
                        Money.of("5", "USD"), observed, KnowledgeFixtures.sampleProvenance()));
        return new DestinationCandidate(
                id, destination.slug(), destination.countryCode(), CoverageLevel.FULL,
                EnumSet.of(PoiCategory.FOOD, PoiCategory.SIGHT), months, prices, 2);
    }

    /** In-memory KnowledgePort with one FULL destination and the catalogue rows the tools need. */
    static final class FakeKnowledge implements KnowledgePort {

        private final Map<UUID, Destination> byId = new HashMap<>();
        private final Map<UUID, DestinationGuide> guides = new HashMap<>();
        private final Map<UUID, List<DestinationArea>> areas = new HashMap<>();
        private final Map<UUID, List<Poi>> pois = new HashMap<>();
        private final Map<UUID, List<SeasonalityMonth>> seasonality = new HashMap<>();
        private final Map<UUID, List<PriceObservation>> prices = new HashMap<>();
        private final Map<UUID, List<TransportMode>> transport = new HashMap<>();
        private final Map<String, List<TravelApp>> apps = new HashMap<>();

        Destination seedFull(String slug, String country) {
            UUID id = UUID.nameUUIDFromBytes(slug.getBytes());
            Destination destination = new Destination(
                    id, slug, slug.replace("-", " "), country, "Asia/Tokyo", 35.0, 139.0,
                    CoverageLevel.FULL);
            byId.put(id, destination);
            guides.put(id, new DestinationGuide(
                    UUID.randomUUID(), id, "en",
                    "Tokyo blends neon and temples for first-time visitors.",
                    "Ramen and sushi dominate the food scene.",
                    "Suica cards work almost everywhere.",
                    KnowledgeFixtures.sampleProvenance(), 1));
            areas.put(id, List.of(new DestinationArea(
                    UUID.randomUUID(), id, "shinjuku", "Shinjuku", "Busy hub",
                    35.6, 139.7, KnowledgeFixtures.sampleProvenance())));
            pois.put(id, List.of(new Poi(
                    UUID.randomUUID(), id, null, "ichiran-ramen", "Ichiran Ramen",
                    "Tonkotsu ramen counter", PoiCategory.FOOD, List.of("ramen"), "en",
                    null, null, null, PriceBand.MODERATE, KnowledgeFixtures.sampleProvenance(), 1)));
            List<SeasonalityMonth> months = new ArrayList<>();
            for (int m = 1; m <= 12; m++) {
                months.add(new SeasonalityMonth(
                        UUID.randomUUID(), id, m, WeatherBand.MILD, CrowdBand.MODERATE,
                        PriceBand.MODERATE, null, KnowledgeFixtures.sampleProvenance()));
            }
            seasonality.put(id, months);
            LocalDate observed = LocalDate.of(2026, 1, 1);
            prices.put(id, List.of(
                    new PriceObservation(UUID.randomUUID(), id, "HOTEL_NIGHT",
                            Money.of("120", "USD"), observed, KnowledgeFixtures.sampleProvenance()),
                    new PriceObservation(UUID.randomUUID(), id, "MEAL_MID_RANGE",
                            Money.of("15", "USD"), observed, KnowledgeFixtures.sampleProvenance()),
                    new PriceObservation(UUID.randomUUID(), id, "TRANSIT_DAY_PASS",
                            Money.of("5", "USD"), observed, KnowledgeFixtures.sampleProvenance())));
            transport.put(id, List.of(new TransportMode(
                    UUID.randomUUID(), id, "metro", "Tokyo Metro", TransportKind.METRO,
                    "Extensive", PriceBand.BUDGET, true, KnowledgeFixtures.sampleProvenance())));
            apps.put(country, List.of(new TravelApp(
                    UUID.randomUUID(), country, "google-maps", "Google Maps",
                    TravelAppCategory.NAVIGATION, "Maps",
                    "https://apps.apple.com/app/maps", null,
                    KnowledgeFixtures.sampleProvenance())));
            return destination;
        }

        @Override
        public Optional<Destination> findDestinationBySlug(String slug) {
            return byId.values().stream().filter(d -> d.slug().equals(slug)).findFirst();
        }

        @Override
        public Optional<Destination> findDestinationById(UUID destinationId) {
            return Optional.ofNullable(byId.get(destinationId));
        }

        @Override
        public List<Destination> findSupportedDestinations() {
            return List.copyOf(byId.values());
        }

        @Override
        public Optional<DestinationGuide> findGuide(UUID destinationId, String locale) {
            return Optional.ofNullable(guides.get(destinationId));
        }

        @Override
        public List<DestinationArea> findAreas(UUID destinationId) {
            return areas.getOrDefault(destinationId, List.of());
        }

        @Override
        public List<Poi> findPois(UUID destinationId, Optional<PoiCategory> category) {
            return pois.getOrDefault(destinationId, List.of()).stream()
                    .filter(p -> category.isEmpty() || p.category() == category.get())
                    .toList();
        }

        @Override
        public List<TransportMode> findTransportModes(UUID destinationId) {
            return transport.getOrDefault(destinationId, List.of());
        }

        @Override
        public List<RouteSegment> findRouteSegments(UUID destinationId) {
            return List.of();
        }

        @Override
        public List<TravelApp> findTravelApps(String countryCode) {
            return apps.getOrDefault(countryCode, List.of());
        }

        @Override
        public List<TravelAppReplacement> findTravelAppReplacements(String countryCode) {
            return List.of();
        }

        @Override
        public List<SeasonalityMonth> findSeasonality(UUID destinationId) {
            return seasonality.getOrDefault(destinationId, List.of());
        }

        @Override
        public List<PriceObservation> findPriceHistory(UUID destinationId, String category) {
            return prices.getOrDefault(destinationId, List.of()).stream()
                    .filter(p -> p.category().equals(category))
                    .toList();
        }

        @Override
        public List<KnowledgeMatch> search(KnowledgeQuery query) {
            return List.of();
        }
    }
}
