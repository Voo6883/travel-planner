package com.travelplanner.application.tripchat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.application.knowledge.DestinationGuideService;
import com.travelplanner.application.research.ResearchJobService;
import com.travelplanner.application.research.ResearchJobView;
import com.travelplanner.application.research.ResearchRecommendationService;
import com.travelplanner.application.research.ResearchRecommendationsView;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.domain.algorithm.ranking.ScoreBreakdown;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.model.TravelerGuide;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.port.ConversationRepositoryPort;
import com.travelplanner.domain.port.KnowledgePort;
import com.travelplanner.domain.port.ResearchJobRepositoryPort;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Status gates and payloads for C2 chat research tools (task 27). */
class TripChatResearchToolServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final UserContext USER =
            UserContext.of(UUID.randomUUID(), "t@example.com", Role.USER);

    private TripAccess access;
    private ResearchJobService jobs;
    private ResearchJobRepositoryPort jobRows;
    private ResearchRecommendationService recommendations;
    private DestinationGuideService guides;
    private KnowledgePort knowledge;
    private ConversationRepositoryPort conversations;
    private TripChatResearchToolService service;
    private UUID tripId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        access = mock(TripAccess.class);
        jobs = mock(ResearchJobService.class);
        jobRows = mock(ResearchJobRepositoryPort.class);
        recommendations = mock(ResearchRecommendationService.class);
        guides = mock(DestinationGuideService.class);
        knowledge = mock(KnowledgePort.class);
        conversations = mock(ConversationRepositoryPort.class);
        when(conversations.allocateSequence(any(), any())).thenReturn(1L, 2L);
        when(conversations.findConversationByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        service = new TripChatResearchToolService(access, jobs, jobRows, recommendations, guides,
                knowledge, conversations, new ObjectMapper());
        tripId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
    }

    @Test
    void startResearchQueuesAndEmitsResearchStarted() {
        Trip trip = trip(TripStatus.BRIEF_COMPLETE);
        when(access.requireEditable(tripId, USER)).thenReturn(trip);
        UUID jobId = UUID.randomUUID();
        when(jobs.start(any(), eq(USER))).thenReturn(new ResearchJobView(
                jobId, tripId, ResearchJobStatus.QUEUED, 0, null, 0, null, null));

        TripChatResearchToolService.Result result = service.startResearch(context(
                TripChatTools.START_RESEARCH),
                StartResearchArgs.parse(TripChatTools.START_RESEARCH,
                        "{\"user_confirmed\":true}", new ObjectMapper()));

        assertThat(result.eventKind()).isEqualTo(TripChatResearchToolService.EventKind.RESEARCH_STARTED);
        assertThat(result.jobId()).isEqualTo(jobId);
        assertThat(result.payloadJson()).contains(jobId.toString());
        verify(conversations, org.mockito.Mockito.times(2)).appendMessage(any());
    }

    @Test
    void startResearchIsRefusedOutsideBriefComplete() {
        when(access.requireEditable(tripId, USER)).thenReturn(trip(TripStatus.DRAFT));
        assertThatThrownBy(() -> service.startResearch(context(TripChatTools.START_RESEARCH),
                StartResearchArgs.parse(TripChatTools.START_RESEARCH,
                        "{\"user_confirmed\":true}", new ObjectMapper())))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void researchStatusReturnsPersistedProgressOnly() {
        when(access.requireOwned(tripId, USER)).thenReturn(trip(TripStatus.RESEARCH_RUNNING));
        ResearchJob job = ResearchJob.queue(tripId, USER.userId(), NOW).markRunning(NOW)
                .markProgress(42, NOW);
        when(jobRows.findLatestByTripId(tripId)).thenReturn(Optional.of(job));

        TripChatResearchToolService.Result result = service.researchStatus(
                context(TripChatTools.GET_RESEARCH_STATUS),
                EmptyToolArgs.parse(TripChatTools.GET_RESEARCH_STATUS, "{}", new ObjectMapper()));

        assertThat(result.eventKind()).isEqualTo(TripChatResearchToolService.EventKind.NONE);
        assertThat(result.payloadJson()).contains("\"progress_pct\":42");
        assertThat(result.payloadJson()).contains("RUNNING");
    }

    @Test
    void recommendationsSummaryGroundsInPersistedRows() {
        when(access.requireOwned(tripId, USER)).thenReturn(trip(TripStatus.RESEARCH_READY));
        RankedRecommendation row = ranked();
        ResearchRunResult run = ResearchRunResult.ranked(
                row.researchRunId(), tripId, USER.userId(), "destination-ranker-v1",
                "travel-research-stub", 1, "stub", List.of(), List.of(row), NOW);
        when(recommendations.list(tripId, USER)).thenReturn(new ResearchRecommendationsView(run, null));

        TripChatResearchToolService.Result result = service.recommendationsSummary(
                context(TripChatTools.GET_RECOMMENDATIONS_SUMMARY),
                EmptyToolArgs.parse(TripChatTools.GET_RECOMMENDATIONS_SUMMARY, "{}",
                        new ObjectMapper()));

        assertThat(result.payloadJson()).contains(row.id().toString());
        assertThat(result.payloadJson()).contains("source_refs");
        assertThat(result.payloadJson()).contains(row.destinationSlug());
    }

    @Test
    void selectRecommendationJustPickUsesHighestRank() {
        when(access.requireEditable(tripId, USER)).thenReturn(trip(TripStatus.RESEARCH_READY));
        RankedRecommendation row = ranked();
        ResearchRunResult run = ResearchRunResult.ranked(
                row.researchRunId(), tripId, USER.userId(), "destination-ranker-v1",
                "travel-research-stub", 1, "stub", List.of(), List.of(row), NOW);
        when(recommendations.list(tripId, USER)).thenReturn(new ResearchRecommendationsView(run, null));
        Trip selected = new Trip(tripId, USER.userId(), "Trip", TripStatus.DESTINATION_SELECTED,
                row.id(), 4, NOW, NOW);
        when(recommendations.select(any(), eq(USER))).thenReturn(selected);

        TripChatResearchToolService.Result result = service.selectRecommendation(
                context(TripChatTools.SELECT_RECOMMENDATION),
                SelectRecommendationArgs.parse(TripChatTools.SELECT_RECOMMENDATION,
                        "{\"confirmation\":\"just_pick\"}", new ObjectMapper()));

        assertThat(result.eventKind())
                .isEqualTo(TripChatResearchToolService.EventKind.DESTINATION_SELECTED);
        assertThat(result.payloadJson()).contains(row.id().toString());
    }

    @Test
    void destinationGuideReturnsKbPresenceFlags() {
        when(access.requireOwned(tripId, USER)).thenReturn(trip(TripStatus.RESEARCH_READY));
        UUID destinationId = UUID.randomUUID();
        Destination destination = new Destination(destinationId, "tokyo", "Tokyo", "JP",
                "Asia/Tokyo", null, null, CoverageLevel.FULL);
        when(guides.get(destinationId, "en")).thenReturn(
                new DestinationGuideService.DestinationGuideDetail(
                        destination, Optional.empty(), List.of(), List.of(), List.of(), List.of()));

        TripChatResearchToolService.Result result = service.destinationGuide(
                context(TripChatTools.GET_DESTINATION_GUIDE),
                GetDestinationGuideArgs.parse(TripChatTools.GET_DESTINATION_GUIDE,
                        "{\"destination_id\":\"" + destinationId + "\"}", new ObjectMapper()));

        assertThat(result.payloadJson()).contains("\"guide_present\":false");
        assertThat(result.payloadJson()).contains("tokyo");
    }

    @Test
    void travelAppsReturnsKbRows() {
        when(access.requireOwned(tripId, USER)).thenReturn(trip(TripStatus.RESEARCH_READY));
        when(knowledge.findTravelApps("JP")).thenReturn(List.of());

        TripChatResearchToolService.Result result = service.travelApps(
                context(TripChatTools.GET_TRAVEL_APPS),
                GetTravelAppsArgs.parse(TripChatTools.GET_TRAVEL_APPS,
                        "{\"country_code\":\"JP\"}", new ObjectMapper()));

        assertThat(result.payloadJson()).contains("\"country_code\":\"JP\"");
        assertThat(result.payloadJson()).contains("\"apps\":[]");
    }

    private TripChatToolService.Context context(String tool) {
        return new TripChatToolService.Context(conversationId, tripId, "call-1", tool, USER);
    }

    private Trip trip(TripStatus status) {
        return new Trip(tripId, USER.userId(), "Trip", status, null, 3, NOW, NOW);
    }

    private RankedRecommendation ranked() {
        UUID runId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        ScoreBreakdown breakdown = new ScoreBreakdown(
                0.8, 0.7, 0.6, 0.5, 0.9, 0.85, 0.72, null);
        TravelerGuide guide = new TravelerGuide(
                "Overview", null, List.of(), null, List.of(), null, null, List.of(),
                List.of(RecommendationSourceRef.of("guide:tokyo", "overview")));
        return new RankedRecommendation(
                UUID.randomUUID(), tripId, USER.userId(), runId, destinationId, "tokyo", "JP",
                1, breakdown, "Great food match", guide, List.of(), "Apr",
                List.of(RecommendationSourceRef.of("guide:tokyo", "overview")),
                "destination-ranker-v1", NOW);
    }
}
