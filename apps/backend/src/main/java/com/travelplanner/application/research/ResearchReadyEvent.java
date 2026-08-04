package com.travelplanner.application.research;

import java.util.UUID;

/**
 * Published after a research job reaches {@code COMPLETED} and the trip is {@code RESEARCH_READY}
 * (task 27, UC-C2-08 / UC-N04). Listeners must run after commit — mail is external HTTP.
 */
public record ResearchReadyEvent(UUID jobId, UUID tripId, UUID userId) {
}
