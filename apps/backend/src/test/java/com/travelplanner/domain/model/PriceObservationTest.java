package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.KnowledgeFixtures;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.Money;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class PriceObservationTest {

    private static final KnowledgeProvenance PROVENANCE = KnowledgeFixtures.provenance();
    private static final LocalDate FIRST_OF_MARCH = LocalDate.of(2026, 3, 1);

    @Test
    void keepsTheCurrencyAttachedToTheOnlyActualMoneyInTheCatalogue() {
        PriceObservation observation = observation("HOTEL_NIGHT",
                Money.of("12000", "JPY"), FIRST_OF_MARCH);

        assertThat(observation.category()).isEqualTo("HOTEL_NIGHT");
        assertThat(observation.amount()).isEqualTo(Money.of("12000", "JPY"));
        assertThat(observation.amount().currencyCode()).isEqualTo("JPY");
        assertThat(observation.observedOn()).isEqualTo(FIRST_OF_MARCH);
        assertThat(observation.provenance()).isEqualTo(PROVENANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsABlankCategory(String blank) {
        assertThatThrownBy(() -> observation(blank, Money.of("100", "USD"), FIRST_OF_MARCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category must not be blank");
    }

    @Test
    void rejectsAZeroAmountBecauseAFreeHotelNightIsAFailedImport() {
        // Money already refuses a negative amount, so zero is the only remaining way for a broken
        // import to look like a plausible figure.
        assertThatThrownBy(() -> observation("HOTEL_NIGHT", Money.of("0", "USD"), FIRST_OF_MARCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be positive");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 15, 28, 31})
    void rejectsAnObservationDatedAnythingButTheFirstOfTheMonth(int dayOfMonth) {
        // Normalising the day is what makes "one observation per month" enforceable at all: two
        // sources quoting the same monthly average on different days would otherwise both land.
        LocalDate notTheFirst = LocalDate.of(2026, 3, dayOfMonth);

        assertThatThrownBy(() -> observation("HOTEL_NIGHT", Money.of("100", "USD"), notTheFirst))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be the first of a month");
    }

    @Test
    void acceptsTheFirstOfAnyMonth() {
        assertThat(observation("MEAL_MID_RANGE", Money.of("25.00", "USD"),
                LocalDate.of(2026, 12, 1)).observedOn().getDayOfMonth()).isEqualTo(1);
    }

    @Test
    void rejectsEveryRequiredFieldBeingAbsent() {
        UUID id = UUID.randomUUID();
        Money amount = Money.of("100", "USD");

        assertThatThrownBy(() -> new PriceObservation(null, id, "HOTEL_NIGHT", amount,
                FIRST_OF_MARCH, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriceObservation(id, null, "HOTEL_NIGHT", amount,
                FIRST_OF_MARCH, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriceObservation(id, id, null, amount,
                FIRST_OF_MARCH, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriceObservation(id, id, "HOTEL_NIGHT", null,
                FIRST_OF_MARCH, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriceObservation(id, id, "HOTEL_NIGHT", amount,
                null, PROVENANCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriceObservation(id, id, "HOTEL_NIGHT", amount,
                FIRST_OF_MARCH, null)).isInstanceOf(NullPointerException.class);
    }

    private static PriceObservation observation(String category, Money amount, LocalDate observedOn) {
        return new PriceObservation(UUID.randomUUID(), UUID.randomUUID(), category, amount,
                observedOn, PROVENANCE);
    }
}
