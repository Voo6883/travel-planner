package com.travelplanner.application.research;

import java.util.UUID;

/** Selects one ranked recommendation for a trip (UC-C2-06). */
public record SelectRecommendationCommand(UUID tripId, UUID recommendationId) {
}
