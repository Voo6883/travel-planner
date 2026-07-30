package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.exception.DestinationNotCoveredException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class DestinationTest {

    private static final List<String> SUPPORTED = List.of("tokyo-jp", "bangkok-th", "lisbon-pt");

    @Test
    void carriesTheCuratedIdentityAndCoordinatesOfAFullyCoveredCity() {
        Destination tokyo = destination("tokyo-jp", CoverageLevel.FULL, 35.68, 139.69);

        assertThat(tokyo.slug()).isEqualTo("tokyo-jp");
        assertThat(tokyo.name()).isEqualTo("Tokyo");
        assertThat(tokyo.countryCode()).isEqualTo("JP");
        assertThat(tokyo.timezone()).isEqualTo("Asia/Tokyo");
        assertThat(tokyo.coverageLevel()).isEqualTo(CoverageLevel.FULL);
        assertThat(tokyo.latitudeIfKnown()).contains(35.68);
        assertThat(tokyo.longitudeIfKnown()).contains(139.69);
    }

    @Test
    void treatsUncuratedCoordinatesAsAbsentRatherThanAsZero() {
        Destination unplaced = destination("osaka-jp", CoverageLevel.NONE, null, null);

        assertThat(unplaced.latitudeIfKnown()).isEmpty();
        assertThat(unplaced.longitudeIfKnown()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsABlankSlug(String blank) {
        assertThatThrownBy(() -> destination(blank, CoverageLevel.FULL, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug must be 1..120 characters");
    }

    @Test
    void rejectsASlugLongerThanTheColumnItIsStoredIn() {
        // varchar(120): one character over would be a write failure or a truncation, and the slug
        // is the handle seeds, URLs, and the per-destination HNSW indexes all key on.
        String tooLong = "a".repeat(Destination.MAX_SLUG_LENGTH + 1);

        assertThatThrownBy(() -> destination(tooLong, CoverageLevel.FULL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(destination("a".repeat(Destination.MAX_SLUG_LENGTH), CoverageLevel.FULL, null, null)
                .slug()).hasSize(Destination.MAX_SLUG_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "J", "JPN", "JPY "})
    void rejectsACountryCodeThatIsNotIso3166Alpha2(String countryCode) {
        assertThatThrownBy(() -> new Destination(UUID.randomUUID(), "tokyo-jp", "Tokyo",
                countryCode, "Asia/Tokyo", null, null, CoverageLevel.FULL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO 3166-1 alpha-2");
    }

    @Test
    void rejectsAHalfSetCoordinatePairInEitherDirection() {
        // A lone latitude defaults its partner to zero and silently drops the city on the equator,
        // which reads as data rather than as the omission it is.
        assertThatThrownBy(() -> destination("tokyo-jp", CoverageLevel.FULL, 35.68, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
        assertThatThrownBy(() -> destination("tokyo-jp", CoverageLevel.FULL, null, 139.69))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set together");
    }

    @ParameterizedTest
    @CsvSource({"90.0001, 0", "-90.0001, 0", "180, 0", "-180, 0"})
    void rejectsALatitudeOutsideTheGlobe(double latitude, double longitude) {
        assertThatThrownBy(() -> destination("tokyo-jp", CoverageLevel.FULL, latitude, longitude))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude out of range");
    }

    @ParameterizedTest
    @CsvSource({"0, 180.0001", "0, -180.0001", "0, 360"})
    void rejectsALongitudeOutsideTheGlobe(double latitude, double longitude) {
        assertThatThrownBy(() -> destination("tokyo-jp", CoverageLevel.FULL, latitude, longitude))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude out of range");
    }

    @ParameterizedTest
    @CsvSource({"90, 180", "-90, -180", "0, 0"})
    void acceptsTheExactEdgesOfTheCoordinateRange(double latitude, double longitude) {
        Destination edge = destination("edge-xx", CoverageLevel.FULL, latitude, longitude);

        assertThat(edge.latitude()).isEqualTo(latitude);
        assertThat(edge.longitude()).isEqualTo(longitude);
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new Destination(null, "tokyo-jp", "Tokyo", "JP", "Asia/Tokyo",
                null, null, CoverageLevel.FULL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Destination(id, null, "Tokyo", "JP", "Asia/Tokyo",
                null, null, CoverageLevel.FULL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Destination(id, "tokyo-jp", null, "JP", "Asia/Tokyo",
                null, null, CoverageLevel.FULL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Destination(id, "tokyo-jp", "Tokyo", null, "Asia/Tokyo",
                null, null, CoverageLevel.FULL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Destination(id, "tokyo-jp", "Tokyo", "JP", null,
                null, null, CoverageLevel.FULL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Destination(id, "tokyo-jp", "Tokyo", "JP", "Asia/Tokyo",
                null, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void isRankingEligibleOnlyWhenFullyCurated() {
        assertThat(destination("tokyo-jp", CoverageLevel.FULL, null, null).isRankingEligible()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = CoverageLevel.class, names = {"PARTIAL", "NONE"})
    void isNotRankingEligibleBelowFullCoverage(CoverageLevel level) {
        assertThat(destination("osaka-jp", level, null, null).isRankingEligible()).isFalse();
    }

    @Test
    void requireRankableReturnsQuietlyForAFullyCuratedDestination() {
        Destination tokyo = destination("tokyo-jp", CoverageLevel.FULL, null, null);

        assertThat(tokyo.isRankingEligible()).isTrue();
        tokyo.requireRankable(SUPPORTED);
    }

    @ParameterizedTest
    @EnumSource(value = CoverageLevel.class, names = {"PARTIAL", "NONE"})
    void requireRankableRefusesWithTheSupportedListAttached(CoverageLevel level) {
        // The refusal has to carry the alternatives: a caller that only learns "no" has to make a
        // second call before it can say anything useful, and that is the call that gets skipped.
        Destination osaka = destination("osaka-jp", level, null, null);

        assertThatThrownBy(() -> osaka.requireRankable(SUPPORTED))
                .isInstanceOf(DestinationNotCoveredException.class)
                .hasMessageContaining("osaka-jp")
                .satisfies(thrown -> {
                    DestinationNotCoveredException refusal = (DestinationNotCoveredException) thrown;
                    assertThat(refusal.code()).isEqualTo(DestinationNotCoveredException.CODE);
                    assertThat(refusal.supportedSlugs())
                            .containsExactly("tokyo-jp", "bangkok-th", "lisbon-pt");
                    assertThat(refusal.details()).containsEntry("requested", "osaka-jp");
                });
    }

    @Test
    void rejectsABlankNameThatTheNotNullColumnWouldHappilyStore() {
        // `not null` does not catch '': PostgreSQL considers the empty string a fine non-null value,
        // so it reaches the destination picker as an unclickable blank row.
        assertThatThrownBy(() -> new Destination(UUID.randomUUID(), "tokyo-jp", "   ", "JP",
                "Asia/Tokyo", null, null, CoverageLevel.FULL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Tokio", "Tokyo", "UTC+7", "GMT+09:00", "", "  "})
    void rejectsATimezoneTheSchedulerCouldNotResolveLater(String timezone) {
        // Every one of these is a string the schema accepts and ZoneId.of rejects — so without this
        // check the failure lands in C3's scheduler, long after the seed file left the screen, and
        // the fix is a data migration rather than an edit. `UTC+7` and `GMT+09:00` are the
        // interesting cases: both parse, neither is a region, and a fixed offset ignores the DST an
        // itinerary has to respect.
        assertThatThrownBy(() -> new Destination(UUID.randomUUID(), "tokyo-jp", "Tokyo", "JP",
                timezone, null, null, CoverageLevel.FULL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IANA zone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Asia/Tokyo", "Europe/Lisbon", "America/New_York", "UTC"})
    void acceptsTheZonesTheSeedFilesActuallyUse(String timezone) {
        assertThatCode(() -> new Destination(UUID.randomUUID(), "somewhere", "Somewhere", "JP",
                timezone, null, null, CoverageLevel.FULL)).doesNotThrowAnyException();
    }

    private static Destination destination(String slug, CoverageLevel coverage,
            Double latitude, Double longitude) {
        return new Destination(UUID.randomUUID(), slug, "Tokyo", "JP", "Asia/Tokyo",
                latitude, longitude, coverage);
    }
}
