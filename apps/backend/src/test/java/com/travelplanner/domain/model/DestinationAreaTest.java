package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class DestinationAreaTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();

    @Test
    void describesTheNeighbourhoodLevelThatItinerariesGroupAgainst() {
        DestinationArea shibuya = area("shibuya", "Shibuya", "Crossings and department stores.",
                35.66, 139.70);

        assertThat(shibuya.slug()).isEqualTo("shibuya");
        assertThat(shibuya.name()).isEqualTo("Shibuya");
        assertThat(shibuya.descriptionIfPresent()).contains("Crossings and department stores.");
        assertThat(shibuya.latitudeIfKnown()).contains(35.66);
        assertThat(shibuya.longitudeIfKnown()).contains(139.70);
        assertThat(shibuya.provenance()).isEqualTo(PROVENANCE);
    }

    @Test
    void treatsAPlaceholderAreaAsHavingNoDescriptionRatherThanAnEmptyOne() {
        DestinationArea placeholder = area("shinjuku", "Shinjuku", null, null, null);

        assertThat(placeholder.descriptionIfPresent()).isEmpty();
        assertThat(placeholder.latitudeIfKnown()).isEmpty();
        assertThat(placeholder.longitudeIfKnown()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsABlankSlug(String blank) {
        assertThatThrownBy(() -> area(blank, "Shibuya", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug must be 1..120 characters");
    }

    @Test
    void rejectsASlugLongerThanTheColumnItIsStoredIn() {
        assertThatThrownBy(() -> area("a".repeat(DestinationArea.MAX_SLUG_LENGTH + 1),
                "Shibuya", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(area("a".repeat(DestinationArea.MAX_SLUG_LENGTH), "Shibuya", null, null, null)
                .slug()).hasSize(DestinationArea.MAX_SLUG_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankName(String blank) {
        assertThatThrownBy(() -> area("shibuya", blank, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void rejectsAHalfSetCoordinatePairInEitherDirection() {
        // Same reason as Destination: a lone latitude drops the neighbourhood on the equator, and
        // a plausible-looking wrong coordinate is harder to notice than a missing one.
        assertThatThrownBy(() -> area("shibuya", "Shibuya", null, 35.66, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
        assertThatThrownBy(() -> area("shibuya", "Shibuya", null, null, 139.70))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new DestinationArea(null, id, "shibuya", "Shibuya", null,
                null, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationArea(id, null, "shibuya", "Shibuya", null,
                null, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationArea(id, id, null, "Shibuya", null,
                null, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationArea(id, id, "shibuya", null, null,
                null, null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationArea(id, id, "shibuya", "Shibuya", null,
                null, null, null)).isInstanceOf(NullPointerException.class);
    }

    private static DestinationArea area(String slug, String name, String description,
            Double latitude, Double longitude) {
        return new DestinationArea(UUID.randomUUID(), UUID.randomUUID(), slug, name, description,
                latitude, longitude, PROVENANCE);
    }
}
