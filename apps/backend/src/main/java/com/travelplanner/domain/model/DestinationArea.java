package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.KnowledgeDataClass;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A neighbourhood within a destination — the level C3 schedules against
 * (TRAVEL-KNOWLEDGE-CATALOG §2).
 *
 * <p>Areas exist so an itinerary can group a day geographically without inventing distances. A
 * curated {@code RouteSegment} connects two of these; anything C3 cannot find here it must serve as
 * an estimate rather than as a fact.
 *
 * @param slug unique within its destination rather than globally, matching
 *        {@code uq_destination_area_destination_slug} — "downtown" is a reasonable area name in
 *        more than one city
 * @param latitude absent together with {@code longitude}. A half-set coordinate places the
 *        neighbourhood on the equator, which looks like data rather than like the mistake it is
 */
public record DestinationArea(
        UUID id,
        UUID destinationId,
        String slug,
        String name,
        String description,
        Double latitude,
        Double longitude,
        KnowledgeProvenance provenance) {

    /** Matches {@code destination_area.slug varchar(120)}. */
    public static final int MAX_SLUG_LENGTH = 120;

    public DestinationArea {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(provenance, "provenance");

        if (slug.isBlank() || slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be 1.." + MAX_SLUG_LENGTH + " characters, got '" + slug + "'");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        // ck_destination_area_coordinates_paired.
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be set together");
        }
        // The same range checks Destination carries. Omitting them here was the more dangerous of
        // the two gaps: a swapped lat/lng pair for a city is visibly in the wrong ocean, while a
        // swapped pair for a neighbourhood produces a plausible-looking coordinate that C3 then
        // measures walking distances against.
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    /** Present only when both coordinates were curated. */
    public Optional<Double> latitudeIfKnown() {
        return Optional.ofNullable(latitude);
    }

    public Optional<Double> longitudeIfKnown() {
        return Optional.ofNullable(longitude);
    }

    /** Absent while the area is a placeholder the curator has not written up yet. */
    public Optional<String> descriptionIfPresent() {
        return Optional.ofNullable(description);
    }

    /**
     * ADR 010 §6. An area is curated prose about a neighbourhood, so it ages like the guide
     * narrative it reads as part of — not like the POIs inside it, whose opening hours move
     * independently of anything written here.
     */
    public KnowledgeDataClass dataClass() {
        return KnowledgeDataClass.GUIDE_NARRATIVE;
    }
}
