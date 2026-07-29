package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.enums.TransportKind;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class TransportModeTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();

    @Test
    void recordsTheTouristLegibilityJudgementAModelWouldOtherwiseGuess() {
        TransportMode metro = new TransportMode(UUID.randomUUID(), UUID.randomUUID(), "tokyo-metro",
                "Tokyo Metro", TransportKind.METRO, "Signed in English throughout.",
                PriceBand.BUDGET, true, PROVENANCE);

        assertThat(metro.slug()).isEqualTo("tokyo-metro");
        assertThat(metro.name()).isEqualTo("Tokyo Metro");
        assertThat(metro.kind()).isEqualTo(TransportKind.METRO);
        assertThat(metro.touristFriendly()).isTrue();
        assertThat(metro.costBandIfKnown()).contains(PriceBand.BUDGET);
        assertThat(metro.descriptionIfPresent()).contains("Signed in English throughout.");
        assertThat(metro.provenance()).isEqualTo(PROVENANCE);
    }

    @Test
    void reportsAnUnpricedModeAsUnknownRatherThanFree() {
        // FREE and "nobody recorded the cost" are different facts, and only an Optional can tell
        // them apart — a PriceBand default would make the second look like the first.
        TransportMode mode = mode("city-bus", "City Bus", null, null, false);

        assertThat(mode.costBandIfKnown()).isEmpty();
        assertThat(mode.descriptionIfPresent()).isEmpty();
        assertThat(mode.touristFriendly()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsABlankSlug(String blank) {
        assertThatThrownBy(() -> mode(blank, "Tokyo Metro", null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug must be 1..120 characters");
    }

    @Test
    void rejectsASlugLongerThanTheColumnItIsStoredIn() {
        assertThatThrownBy(() -> mode("a".repeat(TransportMode.MAX_SLUG_LENGTH + 1),
                "Tokyo Metro", null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mode("a".repeat(TransportMode.MAX_SLUG_LENGTH), "Tokyo Metro", null, null, true)
                .slug()).hasSize(TransportMode.MAX_SLUG_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankName(String blank) {
        assertThatThrownBy(() -> mode("tokyo-metro", blank, null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new TransportMode(null, id, "tokyo-metro", "Tokyo Metro",
                TransportKind.METRO, null, null, true, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransportMode(id, null, "tokyo-metro", "Tokyo Metro",
                TransportKind.METRO, null, null, true, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransportMode(id, id, null, "Tokyo Metro",
                TransportKind.METRO, null, null, true, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransportMode(id, id, "tokyo-metro", null,
                TransportKind.METRO, null, null, true, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransportMode(id, id, "tokyo-metro", "Tokyo Metro",
                null, null, null, true, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransportMode(id, id, "tokyo-metro", "Tokyo Metro",
                TransportKind.METRO, null, null, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static TransportMode mode(String slug, String name, String description,
            PriceBand costBand, boolean touristFriendly) {
        return new TransportMode(UUID.randomUUID(), UUID.randomUUID(), slug, name,
                TransportKind.METRO, description, costBand, touristFriendly, PROVENANCE);
    }
}
