package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.LegResolution;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * How the traveller gets from one scheduled block to the next (UC-C3-09/10/11).
 *
 * <p><strong>An instance, not a template.</strong> {@link RouteSegment} in the knowledge base is the
 * reusable fact — "Shibuya to Asakusa is about 35 minutes by metro"; this is that fact applied to one
 * traveller's Tuesday afternoon. {@code routeSegmentId} is the citation back to it.
 *
 * <p><strong>The invariants are about honesty, not shape.</strong> An {@code UNKNOWN} leg claims
 * nothing — no duration, no mode, no cost, no citation — because a leg that guesses is worse than a
 * leg that admits it does not know, and worse still than no leg at all, which would read as "these
 * two stops are adjacent". Everything else must carry both a duration and a mode. The same two rules
 * are {@code ck_itinerary_leg_unknown_claims_nothing} and
 * {@code ck_itinerary_leg_resolved_has_duration} in V28: enforced twice because this is the field a
 * traveller acts on.
 *
 * @param recommendedAppIds UC-C3-11, already filtered through the suppression rules (UC-K14) — no
 *        Uber where 滴滴 is the local standard. Empty is a legitimate answer for a walk
 * @param sourceRef the citation behind the claim, copied so the timeline can show it without
 *        re-reading the knowledge base
 */
public record ItineraryLeg(
        UUID id,
        UUID fromItemId,
        UUID toItemId,
        LegResolution resolution,
        TransportKind mode,
        Integer durationMinutes,
        PriceBand costBand,
        UUID routeSegmentId,
        String sourceRef,
        String instructions,
        List<UUID> recommendedAppIds) {

    /** {@link LegResolution#SAME_AREA_WALK}'s declared constant — see the enum for why not computed. */
    public static final int SAME_AREA_WALK_MINUTES = 15;

    public ItineraryLeg {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fromItemId, "fromItemId");
        Objects.requireNonNull(toItemId, "toItemId");
        Objects.requireNonNull(resolution, "resolution");
        recommendedAppIds = List.copyOf(
                Objects.requireNonNull(recommendedAppIds, "recommendedAppIds"));

        if (fromItemId.equals(toItemId)) {
            throw new IllegalArgumentException("a leg must join two different items");
        }
        if (resolution.carriesDuration()) {
            if (durationMinutes == null || mode == null) {
                throw new IllegalArgumentException(
                        resolution + " must carry both a duration and a mode");
            }
            if (durationMinutes < 1) {
                throw new IllegalArgumentException(
                        "durationMinutes must be at least 1, got " + durationMinutes);
            }
        } else if (durationMinutes != null || mode != null || costBand != null
                || routeSegmentId != null) {
            // The whole point of UNKNOWN. A duration attached to it would be the fabricated
            // precision the brief forbids in as many words.
            throw new IllegalArgumentException(
                    "an UNKNOWN leg must claim no duration, mode, cost band or segment");
        }
        // Only a curated segment may cite one: an estimate pointing at a segment is a measurement
        // wearing a citation it did not earn.
        if (routeSegmentId != null && !resolution.isCurated()) {
            throw new IllegalArgumentException(
                    "only CURATED_SEGMENT may cite a route segment, got " + resolution);
        }
    }

    /** Absent exactly when the leg is {@code UNKNOWN}. */
    public Optional<Integer> durationMinutesIfKnown() {
        return Optional.ofNullable(durationMinutes);
    }

    public Optional<TransportKind> modeIfKnown() {
        return Optional.ofNullable(mode);
    }

    public Optional<PriceBand> costBandIfKnown() {
        return Optional.ofNullable(costBand);
    }

    public Optional<UUID> routeSegmentIdIfCited() {
        return Optional.ofNullable(routeSegmentId);
    }

    public Optional<String> sourceRefIfPresent() {
        return Optional.ofNullable(sourceRef);
    }

    public Optional<String> instructionsIfPresent() {
        return Optional.ofNullable(instructions);
    }

    /** UC-C3-09: the traveller has to work this one out themselves. */
    public boolean needsUserAttention() {
        return resolution.needsUserAttention();
    }

    /** Whether the duration came from curation rather than from a fallback. */
    public boolean isCurated() {
        return resolution.isCurated();
    }
}
