package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A single place worth going to (TRAVEL-KNOWLEDGE-CATALOG §2, ADR 010 §5 and §6).
 *
 * <p>The two nullable detail fields carry the shortest TTL in the whole TKB — 90 days, per
 * {@code KnowledgeDataClass.POI_DETAILS} — because ADR 010 §6 puts it plainly: a stale
 * opening-hours presented as fact is the most direct way to ruin a traveller's day. Staleness is
 * answered from {@link #provenance()}, which travels with the row precisely so a caller cannot
 * forget to ask.
 *
 * @param areaId nullable: not every POI sits inside a curated area, and forcing one would invent
 *        geography. The database uses {@code ON DELETE SET NULL} for the same reason — removing an
 *        area must not delete the restaurants in it
 * @param tags never null and never shared. ADR 010 §5 embeds {@code name + description + tags} as
 *        one chunk, so a caller mutating this list afterwards would silently desynchronise the row
 *        from its own vector
 * @param openingHours free text on purpose. Real hours are irregular ("closed 2nd Tuesday,
 *        11:00-14:30 in winter") and a structured model that cannot express the exception invites a
 *        confident wrong answer; the agent quotes this string or says it does not know
 * @param priceBand an ordinal band rather than an amount — see {@link PriceObservation} for the one
 *        place actual money lives
 */
public record Poi(
        UUID id,
        UUID destinationId,
        UUID areaId,
        String slug,
        String name,
        String description,
        PoiCategory category,
        List<String> tags,
        String locale,
        Double latitude,
        Double longitude,
        String openingHours,
        PriceBand priceBand,
        KnowledgeProvenance provenance,
        int version) {

    /** Matches {@code poi.slug varchar(160)}. */
    public static final int MAX_SLUG_LENGTH = 160;

    public Poi {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(provenance, "provenance");

        if (slug.isBlank() || slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be 1.." + MAX_SLUG_LENGTH + " characters, got '" + slug + "'");
        }
        // ck_poi_name_not_blank.
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (locale.isBlank()) {
            throw new IllegalArgumentException("locale must not be blank");
        }
        // Mirrors `tags text[] NOT NULL DEFAULT '{}'`. An untagged POI has no tags, not unknown
        // tags, so the absence collapses to empty here instead of forcing every reader to null-check
        // before iterating. List.copyOf also rejects a null element, which would embed as "null".
        tags = tags == null ? List.of() : List.copyOf(tags);

        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be set together");
        }
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative, got " + version);
        }
    }

    /** ADR 010 §1 counts these towards the per-destination floor of eight food POIs. */
    public boolean isFood() {
        return category == PoiCategory.FOOD;
    }

    /** Absent when the POI was not placed inside a curated area. */
    public Optional<UUID> areaIdIfKnown() {
        return Optional.ofNullable(areaId);
    }

    public Optional<String> descriptionIfPresent() {
        return Optional.ofNullable(description);
    }

    public Optional<Double> latitudeIfKnown() {
        return Optional.ofNullable(latitude);
    }

    public Optional<Double> longitudeIfKnown() {
        return Optional.ofNullable(longitude);
    }

    /** Absent when never curated. Present does not mean current — check the provenance TTL. */
    public Optional<String> openingHoursIfKnown() {
        return Optional.ofNullable(openingHours);
    }

    public Optional<PriceBand> priceBandIfKnown() {
        return Optional.ofNullable(priceBand);
    }
}
