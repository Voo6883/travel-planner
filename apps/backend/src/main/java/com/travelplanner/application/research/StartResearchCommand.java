package com.travelplanner.application.research;

import java.util.UUID;

/**
 * The intent to start a research run for a trip (UC-C2-01).
 *
 * <p>Only the trip id: the owner comes from the {@code UserContext} the service also receives, and
 * everything else about the run is the platform's to decide.
 */
public record StartResearchCommand(UUID tripId) {
}
