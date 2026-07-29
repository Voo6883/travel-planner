package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.GuideFieldGroup;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The narrative body for one destination in one locale (ADR 010 §5, PLAN §4.1.2).
 *
 * <p>The three texts are separate components rather than one blob because they are
 * <em>embedded</em> separately. {@code overview}, {@code food} and {@code practical} answer
 * different questions, and a single vector spanning all three is the unusable centroid ADR 010
 * rejects under "Alternatives considered". Merging them here would quietly undo that decision one
 * layer above the schema.
 *
 * <p>{@code overview} is the only required text: a guide with nothing to say about the place is a
 * row that exists to satisfy a coverage count, which is exactly the silent gap ADR 010 §4 exists to
 * prevent. {@code food} and {@code practical} are genuinely optional — a destination may be curated
 * in stages, and an absent section is honest where an empty string is not.
 *
 * @param locale ADR 010 §5: {@code en} is authoritative for v1 embeddings. {@code ms} strings are
 *        translated at presentation and never embedded, so a non-{@code en} guide is display
 *        material rather than retrieval material
 * @param version bumped by task 41's curation UI. Not an ADR 008 optimistic lock — this aggregate
 *        is written by curators, not concurrently by a user and the agent
 */
public record DestinationGuide(
        UUID id,
        UUID destinationId,
        String locale,
        String overview,
        String food,
        String practical,
        KnowledgeProvenance provenance,
        int version) {

    public DestinationGuide {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(overview, "overview");
        Objects.requireNonNull(provenance, "provenance");

        if (locale.isBlank()) {
            throw new IllegalArgumentException("locale must not be blank");
        }
        // Matches ck_destination_guide_overview_not_blank. A whitespace-only overview passes a
        // NOT NULL column and then embeds to a vector of nothing, which retrieves as noise.
        if (overview.isBlank()) {
            throw new IllegalArgumentException("overview must not be blank");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative, got " + version);
        }
    }

    /**
     * The text a given field group embeds, absent when that section was never curated.
     *
     * <p>The switch is exhaustive over {@link GuideFieldGroup} on purpose. Adding a fourth group
     * later must fail to compile here rather than fall through to a default and silently embed
     * nothing for it — a missing vector is invisible in retrieval results.
     */
    public Optional<String> textFor(GuideFieldGroup group) {
        Objects.requireNonNull(group, "group");
        return Optional.ofNullable(switch (group) {
            case OVERVIEW -> overview;
            case FOOD -> food;
            case PRACTICAL -> practical;
        });
    }

    /** Absent until a curator writes the food section. */
    public Optional<String> foodIfPresent() {
        return Optional.ofNullable(food);
    }

    public Optional<String> practicalIfPresent() {
        return Optional.ofNullable(practical);
    }
}
