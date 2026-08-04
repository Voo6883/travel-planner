package com.travelplanner.application.research;

import com.travelplanner.domain.model.ResearchRunResult;
import java.util.UUID;

/** Latest research outcome plus the trip's current selection (UC-C2-03/06). */
public record ResearchRecommendationsView(ResearchRunResult run, UUID selectedRecommendationId) {
}
