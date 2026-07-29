package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.model.Trip;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/trips}.
 *
 * <p>No {@code expected_version}: ADR 008 versions edits to existing state, and there is none here
 * for a concurrent writer to have replaced.
 *
 * <p>The name is the only field. Everything else about a new trip is decided by the server — the
 * owner from the session, {@code DRAFT} as the status, and an empty brief written in the same
 * transaction.
 */
public record CreateTripRequest(
        @NotBlank @Size(max = Trip.MAX_NAME_LENGTH) String name) {
}
