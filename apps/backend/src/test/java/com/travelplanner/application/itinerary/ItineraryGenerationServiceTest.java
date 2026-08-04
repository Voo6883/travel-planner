package com.travelplanner.application.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelplanner.application.mobility.RouteResolutionService;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.DestinationArea;
import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.model.ItineraryProposal;
import com.travelplanner.domain.model.Poi;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.port.ItineraryAgentPort;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Generation orchestration: the status gate, the bounded loop, and who is authoritative.
 *
 * <p>The load-bearing assertions here are the negative ones — that an ungrounded or infeasible
 * proposal never reaches persistence, and that the trip does not move to {@code ITINERARY_READY}
 * unless a plan actually exists. Task 30's Definition of Done is mostly about what must *not*
 * happen.
 */
class ItineraryGenerationServiceTest {

    private static final UUID DESTINATION = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 4, 1);
    private static final LocalDate END = LocalDate.of(2026, 4, 2);

    private KnowledgePort knowledge;
    private ItineraryAgentPort agent;
    private ItineraryPersistenceService itineraries;
    private RouteResolutionService routes;
    private ItineraryStatusWriter statusWriter;
    private ItineraryGenerationService service;

    @BeforeEach
    void setUp() {
        knowledge = mock(KnowledgePort.class);
        agent = mock(ItineraryAgentPort.class);
        itineraries = mock(ItineraryPersistenceService.class);
        routes = mock(RouteResolutionService.class);
        statusWriter = mock(ItineraryStatusWriter.class);
        service = new ItineraryGenerationService(
                knowledge, agent, itineraries, routes, statusWriter);

        when(knowledge.findDestinationById(DESTINATION)).thenReturn(Optional.of(destination()));
        when(knowledge.findPois(any(), any())).thenReturn(pois(6));
        when(knowledge.findAreas(DESTINATION)).thenReturn(List.of(area()));
        when(routes.resolveForItinerary(any())).thenReturn(List.of());
    }

    // --------------------------------------------------------------------------- the status gate

    @Test
    void refusesToGenerateForATripThatHasNotChosenADestination() {
        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.BRIEF_COMPLETE), brief(), DESTINATION, START, END);

        assertThat(outcome.failure())
                .isEqualTo(ItineraryGenerationOutcome.Failure.WRONG_TRIP_STATUS);
        verify(agent, never()).propose(any());
        verify(statusWriter, never()).markItineraryReady(any());
    }

    @Test
    void refusesWhenNoDestinationIdWasResolved() {
        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.DESTINATION_SELECTED), brief(), null, START, END);

        assertThat(outcome.failure())
                .isEqualTo(ItineraryGenerationOutcome.Failure.NO_SELECTED_DESTINATION);
        verify(agent, never()).propose(any());
    }

    /** The honest answer on a PARTIAL corpus, and distinct from the model failing. */
    @Test
    void reportsInsufficientKnowledgeRatherThanAskingTheAgentForTheImpossible() {
        when(knowledge.findPois(any(), any())).thenReturn(List.of());

        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.DESTINATION_SELECTED), brief(), DESTINATION, START, END);

        assertThat(outcome.failure())
                .isEqualTo(ItineraryGenerationOutcome.Failure.INSUFFICIENT_KNOWLEDGE);
        verify(agent, never()).propose(any());
    }

    // ------------------------------------------------------------------------- the bounded loop

    /** An ungroundable proposal is worth one more sample, and exactly one. */
    @Test
    void retriesOnceWhenTheProposalCannotBeGroundedThenGivesUpTyped() {
        when(agent.propose(any()))
                .thenThrow(ValidationFailedException.field("days[].stops[].poi_id", "invented"));

        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.DESTINATION_SELECTED), brief(), DESTINATION, START, END);

        assertThat(outcome.failure())
                .isEqualTo(ItineraryGenerationOutcome.Failure.UNGROUNDED_PROPOSAL);
        assertThat(outcome.attempts()).isEqualTo(ItineraryGenerationService.MAX_ATTEMPTS);
        verify(agent, times(ItineraryGenerationService.MAX_ATTEMPTS)).propose(any());
        verify(itineraries, never()).save(any());
        verify(statusWriter, never()).markItineraryReady(any());
    }

    /** A second sample that grounds is accepted — that is what the repair budget is for. */
    @Test
    void acceptsARepairedProposalOnTheSecondAttempt() {
        List<Poi> candidates = pois(6);
        when(knowledge.findPois(any(), any())).thenReturn(candidates);
        when(agent.propose(any()))
                .thenThrow(ValidationFailedException.field("days", "wrong shape"))
                .thenReturn(proposal(candidates));
        when(itineraries.save(any())).thenAnswer(call -> Optional.of(
                call.<com.travelplanner.domain.algorithm.scheduling.SchedulingResult>getArgument(0)
                        .itineraryIfBuilt().orElseThrow()));

        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.DESTINATION_SELECTED), brief(), DESTINATION, START, END);

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.attempts()).isEqualTo(2);
        verify(statusWriter).markItineraryReady(any());
    }

    /** Retrying a dead provider is just a slower error, so it is not retried. */
    @Test
    void doesNotRetryAProviderFailure() {
        when(agent.propose(any())).thenThrow(AiProviderException.unavailable("provider down"));

        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.DESTINATION_SELECTED), brief(), DESTINATION, START, END);

        assertThat(outcome.failure())
                .isEqualTo(ItineraryGenerationOutcome.Failure.PROVIDER_UNAVAILABLE);
        assertThat(outcome.attempts()).isEqualTo(1);
        verify(agent, times(1)).propose(any());
    }

    // ------------------------------------------------------------- the validators are in charge

    /**
     * The heart of the task: a proposal the scheduler cannot fill is never persisted, and the trip
     * does not advance. The agent does not get a vote.
     */
    @Test
    void neverPersistsAPlanTheSchedulerRefuses() {
        // Every day empty: the scheduler reports INFEASIBLE and nothing may be written.
        when(agent.propose(any())).thenReturn(emptyProposal());

        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.DESTINATION_SELECTED), brief(), DESTINATION, START, END);

        assertThat(outcome.failure())
                .isEqualTo(ItineraryGenerationOutcome.Failure.NOT_SCHEDULABLE);
        verify(itineraries, never()).save(any());
        verify(statusWriter, never()).markItineraryReady(any());
    }

    @Test
    void resolvesRoutesAndAdvancesTheTripOnlyAfterAPlanIsPersisted() {
        List<Poi> candidates = pois(6);
        when(knowledge.findPois(any(), any())).thenReturn(candidates);
        when(agent.propose(any())).thenReturn(proposal(candidates));
        when(itineraries.save(any())).thenAnswer(call -> Optional.of(
                call.<com.travelplanner.domain.algorithm.scheduling.SchedulingResult>getArgument(0)
                        .itineraryIfBuilt().orElseThrow()));

        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.DESTINATION_SELECTED), brief(), DESTINATION, START, END);

        assertThat(outcome.succeeded()).isTrue();
        Itinerary saved = outcome.itineraryIfBuilt().orElseThrow();
        assertThat(saved.days()).hasSize(2);
        assertThat(saved.isPublishable()).isTrue();
        verify(routes).resolveForItinerary(saved);
        verify(statusWriter).markItineraryReady(any());
    }

    /** Persistence declining is treated as the plan failing, not as a success with no plan. */
    @Test
    void reportsAFailureWhenPersistenceDeclinesThePlan() {
        List<Poi> candidates = pois(6);
        when(knowledge.findPois(any(), any())).thenReturn(candidates);
        when(agent.propose(any())).thenReturn(proposal(candidates));
        when(itineraries.save(any())).thenReturn(Optional.empty());

        ItineraryGenerationOutcome outcome = service.generate(
                trip(TripStatus.DESTINATION_SELECTED), brief(), DESTINATION, START, END);

        assertThat(outcome.succeeded()).isFalse();
        verify(statusWriter, never()).markItineraryReady(any());
    }

    // ------------------------------------------------------------------------------------ setup

    private static Trip trip(TripStatus status) {
        UUID selected = status == TripStatus.DESTINATION_SELECTED ? UUID.randomUUID() : null;
        return new Trip(UUID.randomUUID(), UUID.randomUUID(), "Tokyo trip", status, selected, 0,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static TripBrief brief() {
        return new TripBrief(UUID.randomUUID(), UUID.randomUUID(), List.of("tokyo-jp"), false,
                null, null, null, null, null, List.of(), TravelPace.MODERATE, 0,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static Destination destination() {
        return new Destination(DESTINATION, "tokyo-jp", "Tokyo", "JP", "Asia/Tokyo", null, null,
                CoverageLevel.FULL);
    }

    private static DestinationArea area() {
        return new DestinationArea(AREA, DESTINATION, "shibuya", "Shibuya", null, null, null,
                provenance());
    }

    private static List<Poi> pois(int count) {
        List<Poi> pois = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pois.add(new Poi(UUID.randomUUID(), DESTINATION, AREA, "p-" + i, "P " + i, null,
                    PoiCategory.SIGHT, List.of(), "en", null, null, null, null, provenance(), 0));
        }
        return pois;
    }

    /**
     * A proposal the scheduler can fill, built over candidates the caller has already stubbed.
     *
     * <p>Takes the list rather than stubbing {@code findPois} itself: doing that inside a
     * {@code thenReturn(...)} argument is nested stubbing, which Mockito rejects with
     * {@code UnfinishedStubbingException}.
     */
    private static ItineraryProposal proposal(List<Poi> candidates) {
        return new ItineraryProposal(List.of(
                new ItineraryProposal.ProposedDay(1, AREA,
                        List.of(new ItineraryProposal.ProposedStop(candidates.get(0).id(), null),
                                new ItineraryProposal.ProposedStop(candidates.get(1).id(), null)),
                        null),
                new ItineraryProposal.ProposedDay(2, AREA,
                        List.of(new ItineraryProposal.ProposedStop(candidates.get(2).id(), null)),
                        null)),
                "A plan.", "itinerary-stub", 1, "stub");
    }

    private static ItineraryProposal emptyProposal() {
        return new ItineraryProposal(List.of(
                new ItineraryProposal.ProposedDay(1, AREA, List.of(), null),
                new ItineraryProposal.ProposedDay(2, AREA, List.of(), null)),
                "Nothing to do.", "itinerary-stub", 1, "stub");
    }

    private static KnowledgeProvenance provenance() {
        return new KnowledgeProvenance("wikivoyage:tokyo", "Wikivoyage",
                KnowledgeLicence.CC_BY_SA_4_0, "© Wikivoyage contributors, CC BY-SA 4.0", null,
                TrustTier.COMMUNITY, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
