package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class PoiTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();
    private static final UUID AREA_ID = UUID.randomUUID();

    @Test
    void carriesTheDetailFieldsTheAgentQuotesRatherThanParaphrases() {
        Poi poi = new Poi(UUID.randomUUID(), UUID.randomUUID(), AREA_ID, "tsukiji-outer-market",
                "Tsukiji Outer Market", "Stalls and knife shops.", PoiCategory.FOOD,
                List.of("seafood", "market"), "en", 35.66, 139.77,
                "05:00-14:00, closed Sundays", PriceBand.BUDGET, PROVENANCE, 2);

        assertThat(poi.name()).isEqualTo("Tsukiji Outer Market");
        assertThat(poi.tags()).containsExactly("seafood", "market");
        assertThat(poi.areaIdIfKnown()).contains(AREA_ID);
        assertThat(poi.descriptionIfPresent()).contains("Stalls and knife shops.");
        assertThat(poi.latitudeIfKnown()).contains(35.66);
        assertThat(poi.longitudeIfKnown()).contains(139.77);
        assertThat(poi.openingHoursIfKnown()).contains("05:00-14:00, closed Sundays");
        assertThat(poi.priceBandIfKnown()).contains(PriceBand.BUDGET);
        assertThat(poi.version()).isEqualTo(2);
    }

    @Test
    void reportsUncuratedDetailsAsAbsentRatherThanGuessingThem() {
        Poi bare = poi("senso-ji", PoiCategory.SIGHT, List.of());

        assertThat(bare.areaIdIfKnown()).isEmpty();
        assertThat(bare.descriptionIfPresent()).isEmpty();
        assertThat(bare.latitudeIfKnown()).isEmpty();
        assertThat(bare.longitudeIfKnown()).isEmpty();
        assertThat(bare.openingHoursIfKnown()).isEmpty();
        assertThat(bare.priceBandIfKnown()).isEmpty();
    }

    @Test
    void collapsesAbsentTagsToAnEmptyListSoNoReaderHasToNullCheckBeforeIterating() {
        // Mirrors `tags text[] NOT NULL DEFAULT '{}'`: an untagged POI has no tags, not unknown
        // ones, so there is nothing for a caller to distinguish.
        assertThat(poi("senso-ji", PoiCategory.SIGHT, null).tags()).isEmpty();
    }

    @Test
    void copiesTheTagsSoAPoiCannotBeDesynchronisedFromItsOwnEmbedding() {
        // ADR 010 §5 embeds name + description + tags as one chunk. A caller that could append a
        // tag afterwards would leave the row claiming a tag its vector never saw.
        List<String> callerTags = new ArrayList<>(List.of("temple"));
        Poi poi = poi("senso-ji", PoiCategory.SIGHT, callerTags);

        callerTags.add("smuggled-in");

        assertThat(poi.tags()).containsExactly("temple");
        assertThatThrownBy(() -> poi.tags().add("smuggled-in"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsANullTagBecauseItWouldEmbedAsTheWordNull() {
        assertThatThrownBy(() -> poi("senso-ji", PoiCategory.SIGHT, Arrays.asList("temple", null)))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsABlankSlug(String blank) {
        assertThatThrownBy(() -> poi(blank, PoiCategory.SIGHT, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug must be 1..160 characters");
    }

    @Test
    void rejectsASlugLongerThanTheColumnItIsStoredIn() {
        assertThatThrownBy(() -> poi("a".repeat(Poi.MAX_SLUG_LENGTH + 1), PoiCategory.SIGHT, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(poi("a".repeat(Poi.MAX_SLUG_LENGTH), PoiCategory.SIGHT, List.of()).slug())
                .hasSize(Poi.MAX_SLUG_LENGTH);
    }

    @Test
    void rejectsABlankNameOrLocale() {
        assertThatThrownBy(() -> new Poi(UUID.randomUUID(), UUID.randomUUID(), null, "senso-ji",
                "  ", null, PoiCategory.SIGHT, List.of(), "en", null, null, null, null,
                PROVENANCE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
        assertThatThrownBy(() -> new Poi(UUID.randomUUID(), UUID.randomUUID(), null, "senso-ji",
                "Senso-ji", null, PoiCategory.SIGHT, List.of(), " ", null, null, null, null,
                PROVENANCE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("locale must not be blank");
    }

    @Test
    void rejectsAHalfSetCoordinatePairInEitherDirection() {
        assertThatThrownBy(() -> poiAt(35.66, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
        assertThatThrownBy(() -> poiAt(null, 139.77))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
    }

    @ParameterizedTest
    @CsvSource({"90.0001, 0", "-90.0001, 0", "1000, 0"})
    void rejectsALatitudeOutsideTheGlobe(double latitude, double longitude) {
        assertThatThrownBy(() -> poiAt(latitude, longitude))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude out of range");
    }

    @ParameterizedTest
    @CsvSource({"0, 180.0001", "0, -180.0001", "0, 1000"})
    void rejectsALongitudeOutsideTheGlobe(double latitude, double longitude) {
        assertThatThrownBy(() -> poiAt(latitude, longitude))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude out of range");
    }

    @ParameterizedTest
    @CsvSource({"90, 180", "-90, -180"})
    void acceptsTheExactEdgesOfTheCoordinateRange(double latitude, double longitude) {
        Poi edge = poiAt(latitude, longitude);

        assertThat(edge.latitudeIfKnown()).contains(latitude);
        assertThat(edge.longitudeIfKnown()).contains(longitude);
    }

    @Test
    void rejectsANegativeVersion() {
        assertThatThrownBy(() -> new Poi(UUID.randomUUID(), UUID.randomUUID(), null, "senso-ji",
                "Senso-ji", null, PoiCategory.SIGHT, List.of(), "en", null, null, null, null,
                PROVENANCE, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must not be negative");
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new Poi(null, id, null, "senso-ji", "Senso-ji", null,
                PoiCategory.SIGHT, List.of(), "en", null, null, null, null, PROVENANCE, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Poi(id, null, null, "senso-ji", "Senso-ji", null,
                PoiCategory.SIGHT, List.of(), "en", null, null, null, null, PROVENANCE, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Poi(id, id, null, null, "Senso-ji", null,
                PoiCategory.SIGHT, List.of(), "en", null, null, null, null, PROVENANCE, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Poi(id, id, null, "senso-ji", null, null,
                PoiCategory.SIGHT, List.of(), "en", null, null, null, null, PROVENANCE, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Poi(id, id, null, "senso-ji", "Senso-ji", null,
                null, List.of(), "en", null, null, null, null, PROVENANCE, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Poi(id, id, null, "senso-ji", "Senso-ji", null,
                PoiCategory.SIGHT, List.of(), null, null, null, null, null, PROVENANCE, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Poi(id, id, null, "senso-ji", "Senso-ji", null,
                PoiCategory.SIGHT, List.of(), "en", null, null, null, null, null, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void countsOnlyFoodCategoryPoisTowardsTheFoodFloor() {
        // ADR 010 §1 sets a floor of eight food POIs per destination, and the seed validator counts
        // them through this predicate.
        assertThat(poi("tsukiji", PoiCategory.FOOD, List.of()).isFood()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PoiCategory.class, names = "FOOD", mode = EnumSource.Mode.EXCLUDE)
    void doesNotCountANonFoodPoiTowardsTheFoodFloor(PoiCategory category) {
        assertThat(poi("senso-ji", category, List.of()).isFood()).isFalse();
    }

    private static Poi poi(String slug, PoiCategory category, List<String> tags) {
        return new Poi(UUID.randomUUID(), UUID.randomUUID(), null, slug, "Senso-ji", null,
                category, tags, "en", null, null, null, null, PROVENANCE, 0);
    }

    private static Poi poiAt(Double latitude, Double longitude) {
        return new Poi(UUID.randomUUID(), UUID.randomUUID(), null, "senso-ji", "Senso-ji", null,
                PoiCategory.SIGHT, List.of(), "en", latitude, longitude, null, null, PROVENANCE, 0);
    }
}
