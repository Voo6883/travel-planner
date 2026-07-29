package com.travelplanner.application.trip;

import com.travelplanner.domain.model.TripBriefDetails;
import java.util.UUID;

/**
 * {@code PUT /api/v1/trips/{tripId}/brief} — the whole-body save the debounced intake form issues
 * (PLAN §4.2, ADR 008).
 *
 * <p>{@code expectedVersion} is the version of the <em>brief</em>, not of the trip. They are
 * separate aggregates with separate {@code @Version} columns, and the agent can advance one without
 * touching the other, so sharing a version between them would report conflicts that did not happen
 * and miss ones that did.
 */
public record SaveTripBriefCommand(UUID tripId, int expectedVersion, TripBriefDetails details) {
}
