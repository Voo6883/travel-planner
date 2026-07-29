package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.CrowdBand;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.WeatherBand;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What one calendar month is like in one destination (PLAN §4.1.2, ADR 010 §6).
 *
 * <p>Feeds C2's {@code fitScore}. All three bands are required because a month that answers only
 * one of "what is the weather, how busy is it, what does it cost" scores as partially-known in a
 * sum that cannot tell the difference between "mild" and "unrecorded".
 *
 * @param month 1-12, a recurring shape rather than an event. Storing a date instead would invite
 *        somebody to filter it by year. ADR 010 §1 requires all twelve before a destination counts
 *        as {@code FULL}
 */
public record SeasonalityMonth(
        UUID id,
        UUID destinationId,
        int month,
        WeatherBand weatherBand,
        CrowdBand crowdBand,
        PriceBand priceBand,
        String notes,
        KnowledgeProvenance provenance) {

    public SeasonalityMonth {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(weatherBand, "weatherBand");
        Objects.requireNonNull(crowdBand, "crowdBand");
        Objects.requireNonNull(priceBand, "priceBand");
        Objects.requireNonNull(provenance, "provenance");

        // ck_seasonality_month_range. A zero-based month is the classic off-by-one here, and it
        // would silently shift a whole year's curation by one column.
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be 1..12, got " + month);
        }
    }

    /** Curator commentary, e.g. a festival week that skews the crowd band. */
    public Optional<String> notesIfPresent() {
        return Optional.ofNullable(notes);
    }
}
