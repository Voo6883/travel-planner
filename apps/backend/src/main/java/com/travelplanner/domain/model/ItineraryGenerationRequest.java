package com.travelplanner.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything the itinerary agent may see (task 30).
 *
 * <p>Handed over complete by the application layer. The agent gets no port, no repository and no
 * network: it can only choose from {@code candidatePois} and {@code areas}, which is what makes
 * "do not invent POIs" structural rather than a line in a prompt. A model that cannot reach the
 * knowledge base cannot cite a place that is not in it — and the guardrails then check that it did
 * not try.
 *
 * @param brief the traveller's own constraints, so the agent selects for interests and pace
 * @param candidatePois every POI curated for the destination. The agent picks a subset
 */
public record ItineraryGenerationRequest(
        UUID tripId,
        UUID destinationId,
        String destinationName,
        LocalDate startDate,
        LocalDate endDate,
        TripBriefDetails brief,
        List<Poi> candidatePois,
        List<DestinationArea> areas) {

    public ItineraryGenerationRequest {
        Objects.requireNonNull(tripId, "tripId");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(destinationName, "destinationName");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(brief, "brief");
        candidatePois = List.copyOf(Objects.requireNonNull(candidatePois, "candidatePois"));
        areas = List.copyOf(Objects.requireNonNull(areas, "areas"));

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must not precede startDate, got " + startDate + " to " + endDate);
        }
    }

    /** Inclusive, so a single-day trip is 1. */
    public int dayCount() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
