package com.travelplanner.domain.model;

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
}
