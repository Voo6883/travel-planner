package com.travelplanner.domain.algorithm.mobility;

import com.travelplanner.domain.model.RouteSegment;
import com.travelplanner.domain.model.TransportMode;
import com.travelplanner.domain.model.TravelApp;
import com.travelplanner.domain.model.TravelAppReplacement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One A→B gap and everything the knowledge base holds that could answer it.
 *
 * <p>Assembled by the application layer from {@code KnowledgePort} and handed over complete, so the
 * policy stays pure and its tests stay tables of records rather than mocks.
 *
 * @param fromAreaId absent when the origin POI was never placed inside a curated area. Absence is
 *        why {@code UNKNOWN} exists — the corpus cannot route from a place it has not located
 * @param segments every curated segment for the destination. The policy filters; the caller does not
 *        pre-select, because "which segment answers this" is the decision under test
 * @param preferredModes the destination's curated modes, used to resolve a segment's
 *        {@code transportModeId} into a kind and a cost band
 */
public record LegRequest(
        UUID fromItemId,
        UUID toItemId,
        UUID fromAreaId,
        UUID toAreaId,
        List<RouteSegment> segments,
        List<TransportMode> modes,
        List<TravelApp> apps,
        List<TravelAppReplacement> replacements) {

    public LegRequest {
        Objects.requireNonNull(fromItemId, "fromItemId");
        Objects.requireNonNull(toItemId, "toItemId");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        modes = List.copyOf(Objects.requireNonNull(modes, "modes"));
        apps = List.copyOf(Objects.requireNonNull(apps, "apps"));
        replacements = List.copyOf(Objects.requireNonNull(replacements, "replacements"));

        if (fromItemId.equals(toItemId)) {
            throw new IllegalArgumentException("a leg must join two different items");
        }
    }

    /** Absent when the origin POI was never placed inside a curated area. */
    public Optional<UUID> fromAreaIdIfKnown() {
        return Optional.ofNullable(fromAreaId);
    }

    public Optional<UUID> toAreaIdIfKnown() {
        return Optional.ofNullable(toAreaId);
    }

    /** True when both stops sit in the same curated neighbourhood — {@code SAME_AREA_WALK}. */
    public boolean isWithinOneArea() {
        return fromAreaId != null && fromAreaId.equals(toAreaId);
    }

    /** True when the corpus has located both ends well enough to look for a segment. */
    public boolean bothEndsLocated() {
        return fromAreaId != null && toAreaId != null;
    }
}
