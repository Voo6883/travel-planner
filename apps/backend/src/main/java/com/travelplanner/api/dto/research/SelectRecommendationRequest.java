package com.travelplanner.api.dto.research;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code POST .../selected-recommendation} (UC-C2-06). */
public record SelectRecommendationRequest(@NotNull UUID recommendationId) {
}
