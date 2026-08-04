package com.travelplanner.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.application.research.ResearchRecommendationService;
import com.travelplanner.application.research.ResearchRecommendationsView;
import com.travelplanner.application.research.SelectRecommendationCommand;
import com.travelplanner.domain.algorithm.ranking.ScoreBreakdown;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ResearchNotReadyException;
import com.travelplanner.domain.model.RankedRecommendation;
import com.travelplanner.domain.model.ResearchRunResult;
import com.travelplanner.domain.model.TravelerGuide;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.RecommendationSourceRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ResearchResultsController.class,
        properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/unused")
@AutoConfigureMockMvc(addFilters = false)
class ResearchResultsControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REC_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DEST_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResearchRecommendationService recommendations;

    @Test
    void listPublishesSnakeCaseRecommendationFields() throws Exception {
        when(recommendations.list(eq(TRIP_ID), any())).thenReturn(view());

        mockMvc.perform(get("/api/v1/trips/{tripId}/ranked-recommendations", TRIP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trip_id").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.research_run_id").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.no_confident_result").value(false))
                .andExpect(jsonPath("$.recommendations[0].recommendation_id")
                        .value(REC_ID.toString()))
                .andExpect(jsonPath("$.recommendations[0].destination_slug").value("kyoto-jp"))
                .andExpect(jsonPath("$.recommendations[0].traveler_guide.overview")
                        .value("Overview"))
                .andExpect(jsonPath("$.recommendations[0].source_refs[0].source_ref")
                        .value("wikivoyage:kyoto"))
                .andExpect(jsonPath("$.recommendations[0].est_cost.amount").value("1200.00"))
                .andExpect(jsonPath("$.recommendations[0].score_breakdown.confidence")
                        .value(0.85));
    }

    @Test
    void listMapsResearchNotReadyTo409() throws Exception {
        when(recommendations.list(eq(TRIP_ID), any())).thenThrow(new ResearchNotReadyException());

        mockMvc.perform(get("/api/v1/trips/{tripId}/ranked-recommendations", TRIP_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("research_not_ready"));
    }

    @Test
    void selectPostsRecommendationIdAndReturnsTrip() throws Exception {
        Trip selected = new Trip(TRIP_ID, USER_ID, "Kyoto", TripStatus.DESTINATION_SELECTED,
                REC_ID, 4, NOW, NOW);
        when(recommendations.select(any(), any())).thenReturn(selected);

        mockMvc.perform(post("/api/v1/trips/{tripId}/selected-recommendation", TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendation_id\":\"" + REC_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESTINATION_SELECTED"))
                .andExpect(jsonPath("$.selected_recommendation_id").value(REC_ID.toString()));

        verify(recommendations).select(any(SelectRecommendationCommand.class), any());
    }

    private static ResearchRecommendationsView view() {
        ScoreBreakdown breakdown = new ScoreBreakdown(
                0.8, 0.7, 0.6, 0.5, 0.9, 0.85, 0.72, Money.of("1200", "USD"));
        TravelerGuide guide = new TravelerGuide(
                "Overview", "Why now", List.of("Gion"), "Food", List.of("Highlight"),
                "Mobility", "Practical", List.of(),
                List.of(RecommendationSourceRef.of("wikivoyage:kyoto", "overview")));
        RankedRecommendation row = new RankedRecommendation(
                REC_ID, TRIP_ID, USER_ID, RUN_ID, DEST_ID, "kyoto-jp", "JP", 1, breakdown,
                "Strong fit", guide, List.of("crowds"), "spring",
                List.of(RecommendationSourceRef.of("wikivoyage:kyoto", "overview")),
                "destination-ranker-v1", NOW);
        ResearchRunResult run = ResearchRunResult.ranked(
                RUN_ID, TRIP_ID, USER_ID, "destination-ranker-v1", "travel-research-stub", 1,
                "stub", List.of(), List.of(row), NOW);
        return new ResearchRecommendationsView(run, null);
    }
}
