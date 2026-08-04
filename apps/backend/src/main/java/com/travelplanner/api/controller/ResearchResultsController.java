package com.travelplanner.api.controller;

import com.travelplanner.api.dto.research.RankedRecommendationsResponse;
import com.travelplanner.api.dto.research.SelectRecommendationRequest;
import com.travelplanner.api.dto.trip.TripResponse;
import com.travelplanner.application.research.ResearchRecommendationService;
import com.travelplanner.application.research.ResearchRecommendationsView;
import com.travelplanner.application.research.SelectRecommendationCommand;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.valueobject.UserContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C2 recommendation list and destination selection (UC-C2-03/05/06).
 *
 * <p>Sibling of {@link ResearchController}: jobs live under {@code .../research/}, while the
 * durable outcome and selection use the kebab-case paths PLAN §4.0 names.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@RequiresDatabase
public class ResearchResultsController {

    private final ResearchRecommendationService recommendations;

    public ResearchResultsController(ResearchRecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    /**
     * Latest ranked recommendations (UC-C2-03/05). {@code 409 research_not_ready} when the trip
     * is not {@code RESEARCH_READY} or {@code DESTINATION_SELECTED} with a selection.
     */
    @GetMapping("/ranked-recommendations")
    public RankedRecommendationsResponse list(
            @PathVariable UUID tripId, @AuthenticationPrincipal UserContext caller) {
        ResearchRecommendationsView view = recommendations.list(tripId, caller);
        return RankedRecommendationsResponse.from(view.run(), view.selectedRecommendationId());
    }

    /** Confirms one recommendation and moves the trip to {@code DESTINATION_SELECTED}. */
    @PostMapping("/selected-recommendation")
    public TripResponse select(
            @PathVariable UUID tripId,
            @Valid @RequestBody SelectRecommendationRequest request,
            @AuthenticationPrincipal UserContext caller) {
        Trip updated = recommendations.select(
                new SelectRecommendationCommand(tripId, request.recommendationId()), caller);
        return TripResponse.from(updated);
    }
}
