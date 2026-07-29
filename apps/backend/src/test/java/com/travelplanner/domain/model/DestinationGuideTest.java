package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.GuideFieldGroup;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class DestinationGuideTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();

    @Test
    void keepsTheThreeSectionsSeparateBecauseTheyEmbedSeparately() {
        DestinationGuide guide = guide("An overview.", "The food.", "The practical bits.");

        assertThat(guide.overview()).isEqualTo("An overview.");
        assertThat(guide.foodIfPresent()).contains("The food.");
        assertThat(guide.practicalIfPresent()).contains("The practical bits.");
        assertThat(guide.locale()).isEqualTo("en");
        assertThat(guide.version()).isEqualTo(3);
        assertThat(guide.provenance()).isEqualTo(PROVENANCE);
    }

    @ParameterizedTest
    @EnumSource(GuideFieldGroup.class)
    void returnsTheTextEachFieldGroupEmbeds(GuideFieldGroup group) {
        DestinationGuide guide = guide("An overview.", "The food.", "The practical bits.");

        assertThat(guide.textFor(group)).isPresent();
    }

    @Test
    void mapsEachFieldGroupToItsOwnSectionRatherThanToAMergedBlob() {
        DestinationGuide guide = guide("An overview.", "The food.", "The practical bits.");

        assertThat(guide.textFor(GuideFieldGroup.OVERVIEW)).contains("An overview.");
        assertThat(guide.textFor(GuideFieldGroup.FOOD)).contains("The food.");
        assertThat(guide.textFor(GuideFieldGroup.PRACTICAL)).contains("The practical bits.");
    }

    @Test
    void reportsAnUncuratedSectionAsAbsentRatherThanAsEmptyText() {
        // An absent section must not embed: a vector built from "" retrieves as noise, which is
        // worse than the section simply not being there.
        DestinationGuide partial = guide("An overview.", null, null);

        assertThat(partial.textFor(GuideFieldGroup.FOOD)).isEmpty();
        assertThat(partial.textFor(GuideFieldGroup.PRACTICAL)).isEmpty();
        assertThat(partial.textFor(GuideFieldGroup.OVERVIEW)).contains("An overview.");
        assertThat(partial.foodIfPresent()).isEmpty();
        assertThat(partial.practicalIfPresent()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsAGuideWithNothingToSayAboutThePlace(String blank) {
        // A blank overview passes the NOT NULL column and then embeds to a vector of nothing —
        // exactly the silent coverage gap ADR 010 §4 exists to prevent.
        assertThatThrownBy(() -> guide(blank, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overview must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void rejectsABlankLocale(String blank) {
        assertThatThrownBy(() -> new DestinationGuide(UUID.randomUUID(), UUID.randomUUID(), blank,
                "An overview.", null, null, PROVENANCE, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locale must not be blank");
    }

    @Test
    void rejectsANegativeVersionButAcceptsAnUnpublishedZero() {
        assertThatThrownBy(() -> new DestinationGuide(UUID.randomUUID(), UUID.randomUUID(), "en",
                "An overview.", null, null, PROVENANCE, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must not be negative");
        assertThat(new DestinationGuide(UUID.randomUUID(), UUID.randomUUID(), "en",
                "An overview.", null, null, PROVENANCE, 0).version()).isZero();
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new DestinationGuide(null, id, "en", "text", null, null,
                PROVENANCE, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationGuide(id, null, "en", "text", null, null,
                PROVENANCE, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationGuide(id, id, null, "text", null, null,
                PROVENANCE, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationGuide(id, id, "en", null, null, null,
                PROVENANCE, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DestinationGuide(id, id, "en", "text", null, null,
                null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void refusesToLookUpTextForNoFieldGroup() {
        DestinationGuide guide = guide("An overview.", null, null);

        assertThatThrownBy(() -> guide.textFor(null)).isInstanceOf(NullPointerException.class);
    }

    private static DestinationGuide guide(String overview, String food, String practical) {
        return new DestinationGuide(UUID.randomUUID(), UUID.randomUUID(), "en",
                overview, food, practical, PROVENANCE, 3);
    }
}
