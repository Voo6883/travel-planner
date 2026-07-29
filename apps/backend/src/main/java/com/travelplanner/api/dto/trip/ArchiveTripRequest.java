package com.travelplanner.api.dto.trip;

import com.travelplanner.api.dto.VersionedMutation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/trips/{tripId}/actions/archive} — ADR 008 §3.
 *
 * <p>A body with one field rather than an empty {@code POST}, because archiving is a mutation of a
 * versioned aggregate like any other: making a trip read-only while the agent is mid-edit loses
 * that edit, and ADR 008's position is that the loser is told rather than silently overruled.
 */
public record ArchiveTripRequest(
        @NotNull @Min(0) Integer expectedVersion) implements VersionedMutation {
}
