package com.travelplanner.domain.model;

import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * How long it takes to get from one area to another by one mode (ADR 010 Consequences).
 *
 * <p><strong>{@link #estimated()} is the honesty flag.</strong> Curated segments exist only for the
 * area pairs somebody actually authored; every other leg is derived from mode heuristics. ADR 010
 * requires that fallback to be explicit rather than LLM-invented, so a leg the curator never wrote
 * has to announce itself as inferred — otherwise a guess and a fact are indistinguishable by the
 * time they reach an itinerary.
 *
 * @param duration positive, and a whole number of minutes. The column is
 *        {@code duration_minutes integer}, so a sub-minute component would be silently truncated on
 *        write and the value read back would not equal the value stored
 */
public record RouteSegment(
        UUID id,
        UUID destinationId,
        UUID fromAreaId,
        UUID toAreaId,
        UUID transportModeId,
        Duration duration,
        boolean estimated,
        String notes,
        KnowledgeProvenance provenance) {

    public RouteSegment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(fromAreaId, "fromAreaId");
        Objects.requireNonNull(toAreaId, "toAreaId");
        Objects.requireNonNull(transportModeId, "transportModeId");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(provenance, "provenance");

        // ck_route_segment_distinct_areas. A segment from an area to itself is a data-entry slip,
        // and would surface as a zero-length leg in an itinerary rather than as an error.
        if (fromAreaId.equals(toAreaId)) {
            throw new IllegalArgumentException(
                    "fromAreaId and toAreaId must differ, both were " + fromAreaId);
        }
        // ck_route_segment_duration_positive.
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive, got " + duration);
        }
        if (duration.toSecondsPart() != 0 || duration.toNanosPart() != 0) {
            throw new IllegalArgumentException(
                    "duration must be a whole number of minutes, got " + duration);
        }
    }

    /** The stored representation. Exact by construction — see the {@code duration} note above. */
    public long durationMinutes() {
        return duration.toMinutes();
    }

    /** Curator commentary, e.g. which exit to use. Absent on most legs. */
    public Optional<String> notesIfPresent() {
        return Optional.ofNullable(notes);
    }
}
