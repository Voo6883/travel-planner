package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.enums.AppReplacementReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The negative half of a country's app pack (tasks/17 "suppress inactive global alternatives").
 *
 * <p>Pure domain test — no Spring context (PLAN §4.0.2-K).
 *
 * <p>Everything here defends one property: a suppression that does not match is worse than no
 * suppression at all. It renders nothing, breaks nothing, and passes review, while the traveller
 * still gets recommended the app that does not work.
 */
class TravelAppReplacementTest {

    private static final UUID LOCAL_APP = UUID.randomUUID();

    @Test
    void carriesTheReplacedAppsDisplayNameRatherThanDerivingItFromTheKey() {
        TravelAppReplacement replacement = replacement("google-maps", "Google Maps",
                AppReplacementReason.NOT_THE_LOCAL_STANDARD);

        // Title-casing the slug would render "Google-Maps", and eventually "Whatsapp".
        assertThat(replacement.replacedAppName()).isEqualTo("Google Maps");
        assertThat(replacement.replacedAppKey()).isEqualTo("google-maps");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Uber", "uber ", " uber", "uber_eats", "uber--eats", "-uber", "uber-",
        "über", "google maps", ""})
    void rejectsAKeyThatIsNotASlugBecauseItWouldSuppressNothing(String key) {
        // The failure mode is silence: the pack renders, the join matches no row, and the warning
        // the row exists to produce is simply absent. There is nothing to notice in review.
        assertThatThrownBy(() -> replacement(key, "Uber", AppReplacementReason.NOT_AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lower-case slug");
    }

    @ParameterizedTest
    @ValueSource(strings = {"uber", "whatsapp", "google-maps", "line", "grab", "apple-pay", "app2"})
    void acceptsTheKeyShapesRealGlobalAppsUse(String key) {
        // A digit is legitimate — `app2` stands in for the versioned names some products carry.
        assertThatCode(() -> replacement(key, "Some App", AppReplacementReason.NOT_AVAILABLE))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAWarningWithNoSentenceInIt() {
        // The category alone cannot carry the market-specific fact, which is the only part a
        // traveller can act on. A blank detail is a red box with nothing to read.
        assertThatThrownBy(() -> new TravelAppReplacement(UUID.randomUUID(), LOCAL_APP, "uber",
                "Uber", AppReplacementReason.NOT_AVAILABLE, "   ", KnowledgeFixtures.provenance()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detail must not be blank");
    }

    @Test
    void rejectsABlankDisplayName() {
        assertThatThrownBy(() -> new TravelAppReplacement(UUID.randomUUID(), LOCAL_APP, "uber", " ",
                AppReplacementReason.NOT_AVAILABLE, "Does not operate here.",
                KnowledgeFixtures.provenance()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replacedAppName must not be blank");
    }

    @Test
    void requiresALocalAlternativeBecauseThatIsTheWholeRecommendation() {
        // A row here means "install this one instead". A suppression with no alternative is a
        // different fact and this type deliberately cannot express it.
        assertThatThrownBy(() -> new TravelAppReplacement(UUID.randomUUID(), null, "uber", "Uber",
                AppReplacementReason.NOT_AVAILABLE, "Does not operate here.",
                KnowledgeFixtures.provenance()))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * The distinction the UI needs. Rendering advice in the same red box as a hard failure trains
     * travellers to ignore the box.
     */
    @ParameterizedTest
    @EnumSource(value = AppReplacementReason.class,
            names = {"NOT_AVAILABLE", "NETWORK_BLOCKED", "NEEDS_LOCAL_PAYMENT"})
    void treatsEveryReasonExceptWeakLocalAdoptionAsUnusable(AppReplacementReason reason) {
        assertThat(replacement("uber", "Uber", reason).replacedAppIsUnusable()).isTrue();
    }

    @Test
    void treatsWeakLocalAdoptionAsAdviceRatherThanAWarning() {
        assertThat(replacement("uber", "Uber", AppReplacementReason.NOT_THE_LOCAL_STANDARD)
                .replacedAppIsUnusable())
                .isFalse();
    }

    @Test
    void matchesTheSuppressedKeyRegardlessOfHowACallerCasedOrPaddedIt() {
        // Callers pass user-facing text and text from other systems; a match that depended on exact
        // casing would be a suppression that works in tests and not in the product.
        TravelAppReplacement replacement =
                replacement("google-maps", "Google Maps", AppReplacementReason.NOT_AVAILABLE);

        assertThat(replacement.suppresses("google-maps")).isTrue();
        assertThat(replacement.suppresses("Google-Maps")).isTrue();
        assertThat(replacement.suppresses("  google-maps  ")).isTrue();
        assertThat(replacement.suppresses("waze")).isFalse();
        assertThat(replacement.suppresses(null)).isFalse();
    }

    private static TravelAppReplacement replacement(String key, String name,
            AppReplacementReason reason) {
        return new TravelAppReplacement(UUID.randomUUID(), LOCAL_APP, key, name, reason,
                "Does not operate in this market.", KnowledgeFixtures.provenance());
    }
}
