package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A way of getting around one destination (TRAVEL-KNOWLEDGE-CATALOG §3).
 *
 * <p>Modes are per destination rather than global because {@code kind} alone does not tell C3 what
 * it needs: "metro" means a tourist-legible network in Tokyo and something else entirely elsewhere.
 * {@link #touristFriendly()} is the field carrying that judgement — the sort of fact a guide states
 * and a model otherwise guesses.
 *
 * @param costBand ordinal, for the same reason {@link Poi#priceBand()} is: comparing raw fares
 *        across currencies and years is meaningless, and C3 only needs "is this the cheap option"
 */
public record TransportMode(
        UUID id,
        UUID destinationId,
        String slug,
        String name,
        TransportKind kind,
        String description,
        PriceBand costBand,
        boolean touristFriendly,
        KnowledgeProvenance provenance) {

    /** Matches {@code transport_mode.slug varchar(120)}. */
    public static final int MAX_SLUG_LENGTH = 120;

    public TransportMode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(provenance, "provenance");

        if (slug.isBlank() || slug.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException(
                    "slug must be 1.." + MAX_SLUG_LENGTH + " characters, got '" + slug + "'");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public Optional<String> descriptionIfPresent() {
        return Optional.ofNullable(description);
    }

    /** Absent when the curator recorded the mode but not what it costs. */
    public Optional<PriceBand> costBandIfKnown() {
        return Optional.ofNullable(costBand);
    }
}
