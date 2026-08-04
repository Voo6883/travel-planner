package com.travelplanner.application.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.research.ResearchTestFakes.InMemoryTrips;
import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.algorithm.ranking.DestinationCandidate;
import com.travelplanner.domain.algorithm.ranking.DestinationRanker;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.enums.WeatherBand;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.DestinationNarrative;
import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.model.SeasonalityMonth;
import com.travelplanner.domain.model.TravelResearchNarratives;
import com.travelplanner.domain.model.TravelerGuide;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.port.TravelResearchAgentPort;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Handler revalidation, ranking merge, and pending-outcome staging (task 25). */
class TravelResearchJobHandlerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final UserContext OWNER =
            UserContext.of(UUID.randomUUID(), "owner@example.com", Role.USER);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryTrips trips;
    private InMemoryBriefs briefs;
    private PendingResearchOutcomeStore pending;
    private AtomicReference<TravelResearchRequestCapture> lastRequest;

    @BeforeEach
    void setUp() {
        trips = new InMemoryTrips();
        briefs = new InMemoryBriefs();
        pending = new PendingResearchOutcomeStore();
        lastRequest = new AtomicReference<>();
    }

    @Test
    void stagesNoConfidentResultWhenNoFullCandidatesExist() {
        Trip trip = seedRunningTrip();
        seedCompleteBrief(trip.id());
        TravelResearchJobHandler handler = handler(request -> {
            lastRequest.set(new TravelResearchRequestCapture(request.ranking().noConfidentResult()));
            return new TravelResearchNarratives(List.of(), "travel-research-stub", 1, "stub");
        }, List.of());
        ResearchJobContext ctx = context(trip);

        handler.execute(ctx);

        ResearchRunResult outcome = pending.take(ctx.jobId()).orElseThrow();
        assertThat(outcome.noConfidentResult()).isTrue();
        assertThat(outcome.recommendations()).isEmpty();
        assertThat(lastRequest.get().noConfident()).isTrue();
    }

    @Test
    void refusesIncompleteBrief() {
        Trip trip = seedRunningTrip();
        briefs.seed(TripBrief.createFor(trip.id(), NOW));
        TravelResearchJobHandler handler = handler(request -> {
            throw new AssertionError("agent must not run");
        }, List.of());

        assertThatThrownBy(() -> handler.execute(context(trip)))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void mergesNarrativesOntoRankedRowsAndStagesOutcome() {
        Trip trip = seedRunningTrip();
        seedCompleteBrief(trip.id());
        DestinationCandidate candidate = fullCandidate("kyoto-jp", "JP");
        TravelResearchAgentPort agent = request -> {
            lastRequest.set(new TravelResearchRequestCapture(request.ranking().noConfidentResult()));
            var ranked = request.rankedDestinations().getFirst();
            TravelerGuide guide = new TravelerGuide(
                    "Kyoto overview", "Why now", List.of("Gion"), "Kaiseki",
                    List.of("fushimi: Fushimi Inari"), "Bus + walk", "Carry cash",
                    List.of(),
                    List.of(RecommendationSourceRef.of("wikivoyage:kyoto", "overview")));
            return new TravelResearchNarratives(List.of(new DestinationNarrative(
                    ranked.destinationId(), ranked.slug(), "Strong culture fit", guide,
                    List.of("crowds at Fushimi"), "spring",
                    List.of(RecommendationSourceRef.of("wikivoyage:kyoto", "overview")))),
                    "travel-research-stub", 1, "stub");
        };
        TravelResearchJobHandler handler = handler(agent, List.of(candidate));
        ResearchJobContext ctx = context(trip);

        handler.execute(ctx);

        ResearchRunResult outcome = pending.take(ctx.jobId()).orElseThrow();
        assertThat(outcome.noConfidentResult()).isFalse();
        assertThat(outcome.recommendations()).hasSize(1);
        assertThat(outcome.recommendations().getFirst().fitScore()).isPositive();
        assertThat(outcome.recommendations().getFirst().rationale()).contains("culture");
        assertThat(outcome.algorithmVersion()).isEqualTo(DestinationRanker.ALGORITHM_VERSION);
        assertThat(lastRequest.get().noConfident()).isFalse();
    }

    @Test
    void providerFailurePropagatesSoPlatformCanRecoverTrip() {
        Trip trip = seedRunningTrip();
        seedCompleteBrief(trip.id());
        DestinationCandidate candidate = fullCandidate("kyoto-jp", "JP");
        TravelResearchJobHandler handler = handler(request -> {
            throw new IllegalStateException("provider blew up");
        }, List.of(candidate));
        ResearchJobContext ctx = context(trip);

        assertThatThrownBy(() -> handler.execute(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider blew up");
        assertThat(pending.take(ctx.jobId())).isEmpty();
    }

    private TravelResearchJobHandler handler(
            TravelResearchAgentPort agent, List<DestinationCandidate> built) {
        return new TravelResearchJobHandler(
                trips, briefs, new FixedCandidateBuilder(built), agent, pending, CLOCK);
    }

    private Trip seedRunningTrip() {
        Trip trip = new Trip(UUID.randomUUID(), OWNER.userId(), "Kyoto",
                TripStatus.RESEARCH_RUNNING, null, 1, NOW, NOW);
        trips.seed(trip);
        return trip;
    }

    private void seedCompleteBrief(UUID tripId) {
        TripBriefDetails details = TripBriefDetails.empty()
                .withDates(new DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 8)))
                .withDateFlexibility(DateFlexibility.FIXED)
                .withDepartureCity("Singapore")
                .withBudget(Money.of("4000", "USD"))
                .withParty(PartySize.ofAdults(2))
                .withInterests(List.of(TravelInterest.FOOD, TravelInterest.SIGHTSEEING))
                .withPace(TravelPace.MODERATE);
        briefs.seed(TripBrief.createFor(tripId, NOW).withDetails(details, NOW));
    }

    private ResearchJobContext context(Trip trip) {
        UUID jobId = UUID.randomUUID();
        return new ResearchJobContext(jobId, jobId, trip.id(), OWNER.userId(), pct -> { });
    }

    private static DestinationCandidate fullCandidate(String slug, String country) {
        UUID id = UUID.nameUUIDFromBytes(slug.getBytes());
        List<SeasonalityMonth> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            months.add(new SeasonalityMonth(
                    UUID.nameUUIDFromBytes((id + "-m" + month).getBytes()),
                    id, month, WeatherBand.MILD, CrowdBand.MODERATE, PriceBand.MODERATE, null,
                    KnowledgeFixtures.provenance()));
        }
        LocalDate observed = LocalDate.of(2026, 1, 1);
        List<PriceObservation> prices = List.of(
                new PriceObservation(UUID.randomUUID(), id, "HOTEL_NIGHT",
                        Money.of("120", "USD"), observed, KnowledgeFixtures.provenance()),
                new PriceObservation(UUID.randomUUID(), id, "MEAL_MID_RANGE",
                        Money.of("15", "USD"), observed, KnowledgeFixtures.provenance()),
                new PriceObservation(UUID.randomUUID(), id, "TRANSIT_DAY_PASS",
                        Money.of("5", "USD"), observed, KnowledgeFixtures.provenance()));
        return new DestinationCandidate(
                id, slug, country, CoverageLevel.FULL,
                EnumSet.of(PoiCategory.FOOD, PoiCategory.SIGHT), months, prices, 4);
    }

    private record TravelResearchRequestCapture(boolean noConfident) {
    }

    private static final class FixedCandidateBuilder extends DestinationCandidateBuilder {
        private final List<DestinationCandidate> fixed;

        FixedCandidateBuilder(List<DestinationCandidate> fixed) {
            super(UnusedKnowledge.INSTANCE);
            this.fixed = fixed;
        }

        @Override
        public List<DestinationCandidate> build(TripBriefDetails brief) {
            return fixed;
        }
    }

    /** KnowledgePort stub that must never be called by FixedCandidateBuilder. */
    private enum UnusedKnowledge implements KnowledgePort {
        INSTANCE;

        @Override
        public Optional<com.travelplanner.domain.model.Destination> findDestinationBySlug(String slug) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.travelplanner.domain.model.Destination> findDestinationById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.travelplanner.domain.model.Destination> findSupportedDestinations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.travelplanner.domain.model.DestinationGuide> findGuide(
                UUID destinationId, String locale) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.travelplanner.domain.model.DestinationArea> findAreas(UUID destinationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.travelplanner.domain.model.Poi> findPois(
                UUID destinationId, Optional<PoiCategory> category) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.travelplanner.domain.model.TransportMode> findTransportModes(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.travelplanner.domain.model.RouteSegment> findRouteSegments(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.travelplanner.domain.model.TravelApp> findTravelApps(String countryCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.travelplanner.domain.model.TravelAppReplacement> findTravelAppReplacements(
                String countryCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SeasonalityMonth> findSeasonality(UUID destinationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PriceObservation> findPriceHistory(UUID destinationId, String category) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.travelplanner.domain.model.KnowledgeMatch> search(
                com.travelplanner.domain.valueobject.KnowledgeQuery query) {
            throw new UnsupportedOperationException();
        }
    }

    static final class InMemoryBriefs implements TripBriefRepositoryPort {
        private final Map<UUID, TripBrief> rows = new LinkedHashMap<>();

        @Override
        public TripBrief save(TripBrief brief) {
            rows.put(brief.tripId(), brief);
            return brief;
        }

        @Override
        public Optional<TripBrief> findByTripId(UUID tripId) {
            return Optional.ofNullable(rows.get(tripId));
        }

        void seed(TripBrief brief) {
            rows.put(brief.tripId(), brief);
        }
    }
}
