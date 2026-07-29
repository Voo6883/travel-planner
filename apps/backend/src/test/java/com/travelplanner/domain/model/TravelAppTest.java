package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.TravelAppCategory;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class TravelAppTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();
    private static final String IOS = "https://apps.apple.com/app/grab";
    private static final String ANDROID = "https://play.google.com/store/apps/details?id=com.grab";

    @Test
    void isScopedToACountryRatherThanToASingleCity() {
        TravelApp grab = new TravelApp(UUID.randomUUID(), "TH", "grab", "Grab",
                TravelAppCategory.RIDEHAILING, "Ride hailing and food delivery.", IOS, ANDROID,
                PROVENANCE);

        assertThat(grab.countryCode()).isEqualTo("TH");
        assertThat(grab.slug()).isEqualTo("grab");
        assertThat(grab.category()).isEqualTo(TravelAppCategory.RIDEHAILING);
        assertThat(grab.iosUrlIfPresent()).contains(IOS);
        assertThat(grab.androidUrlIfPresent()).contains(ANDROID);
        assertThat(grab.descriptionIfPresent()).contains("Ride hailing and food delivery.");
        assertThat(grab.provenance()).isEqualTo(PROVENANCE);
    }

    @Test
    void acceptsASinglePlatformBecauseSomeAppsOnlyShipOnOne() {
        TravelApp iosOnly = app("TH", "grab", IOS, null);
        TravelApp androidOnly = app("TH", "grab", null, ANDROID);

        assertThat(iosOnly.androidUrlIfPresent()).isEmpty();
        assertThat(iosOnly.iosUrlIfPresent()).contains(IOS);
        assertThat(androidOnly.iosUrlIfPresent()).isEmpty();
        assertThat(androidOnly.androidUrlIfPresent()).contains(ANDROID);
        assertThat(androidOnly.descriptionIfPresent()).isEmpty();
    }

    @Test
    void rejectsAnEntryWithNoStoreLinkAtAllBecauseItIsNotActionable() {
        // The whole point of an app pack is "install this before you fly"; an entry the traveller
        // cannot install is a row that costs a screen slot and gives nothing back.
        assertThatThrownBy(() -> app("TH", "grab", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs at least one store link");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "T", "THA", "TH "})
    void rejectsACountryCodeThatIsNotIso3166Alpha2(String countryCode) {
        assertThatThrownBy(() -> app(countryCode, "grab", IOS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO 3166-1 alpha-2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsABlankSlug(String blank) {
        assertThatThrownBy(() -> app("TH", blank, IOS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug must be 1..120 characters");
    }

    @Test
    void rejectsASlugLongerThanTheColumnItIsStoredIn() {
        assertThatThrownBy(() -> app("TH", "a".repeat(TravelApp.MAX_SLUG_LENGTH + 1), IOS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(app("TH", "a".repeat(TravelApp.MAX_SLUG_LENGTH), IOS, null).slug())
                .hasSize(TravelApp.MAX_SLUG_LENGTH);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> new TravelApp(UUID.randomUUID(), "TH", "grab", "  ",
                TravelAppCategory.RIDEHAILING, null, IOS, null, PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new TravelApp(null, "TH", "grab", "Grab",
                TravelAppCategory.RIDEHAILING, null, IOS, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TravelApp(id, null, "grab", "Grab",
                TravelAppCategory.RIDEHAILING, null, IOS, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TravelApp(id, "TH", null, "Grab",
                TravelAppCategory.RIDEHAILING, null, IOS, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TravelApp(id, "TH", "grab", null,
                TravelAppCategory.RIDEHAILING, null, IOS, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TravelApp(id, "TH", "grab", "Grab",
                null, null, IOS, null, PROVENANCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TravelApp(id, "TH", "grab", "Grab",
                TravelAppCategory.RIDEHAILING, null, IOS, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static TravelApp app(String countryCode, String slug, String iosUrl, String androidUrl) {
        return new TravelApp(UUID.randomUUID(), countryCode, slug, "Grab",
                TravelAppCategory.RIDEHAILING, null, iosUrl, androidUrl, PROVENANCE);
    }
}
