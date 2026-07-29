package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.TravelAppCategory;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An app the traveller should install before flying (TRAVEL-KNOWLEDGE-CATALOG §2.1).
 *
 * <p>Scoped by country rather than by destination: Grab is useful across Thailand, not only in
 * Bangkok, and duplicating the row per city would turn a correction into an N-row edit.
 *
 * <p>ADR 010 §6 gives these a 180-day TTL because store URLs rot quietly — an app delisted in one
 * market still returns a page, so age is the only signal available and {@link #provenance()} is
 * where it lives.
 */
public record TravelApp(
        UUID id,
        String countryCode,
        String slug,
        String name,
        TravelAppCategory category,
        String description,
        String iosUrl,
        String androidUrl,
        KnowledgeProvenance provenance) {

    /** Matches {@code travel_app.slug varchar(120)}. */
    public static final int MAX_SLUG_LENGTH = 120;

    public TravelApp {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(provenance, "provenance");

        // ISO 3166-1 alpha-2, matching char(2). Checked here so a three-letter code fails with a
        // message rather than as a truncation.
        if (countryCode.length() != 2) {
            throw new IllegalArgumentException(
                    "countryCode must be ISO 3166-1 alpha-2, got '" + countryCode + "'");
        }
        if (slug.isBlank() || slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be 1.." + MAX_SLUG_LENGTH + " characters, got '" + slug + "'");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        // ck_travel_app_has_a_store_link. An entry with neither link is not actionable, and the
        // whole point of an app pack is "install this before you fly".
        if (iosUrl == null && androidUrl == null) {
            throw new IllegalArgumentException(
                    "travel app '" + slug + "' needs at least one store link");
        }
    }

    /** Absent when the app ships on only one platform, or only one link was curated. */
    public Optional<String> iosUrlIfPresent() {
        return Optional.ofNullable(iosUrl);
    }

    public Optional<String> androidUrlIfPresent() {
        return Optional.ofNullable(androidUrl);
    }

    public Optional<String> descriptionIfPresent() {
        return Optional.ofNullable(description);
    }
}
