package com.travelplanner.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class MoneyTest {

    @Test
    void normalisesScaleSoEqualAmountsAreEqualRegardlessOfHowTheyWereWritten() {
        // This is the property that makes Money usable as a record: BigDecimal.equals compares
        // scale, so without normalisation a value read back from numeric(19,4) would never equal
        // the value that was written.
        assertThat(Money.of("10", "USD")).isEqualTo(Money.of("10.00", "USD"));
        assertThat(Money.of("10", "USD")).hasSameHashCodeAs(Money.of("10.0000", "USD"));
    }

    @Test
    void normalisesToTheCurrencysMinorUnitCount() {
        assertThat(Money.of("1234", "JPY").amount()).isEqualByComparingTo("1234");
        assertThat(Money.of("1234", "JPY").amount().scale()).isZero();
        assertThat(Money.of("1234", "USD").amount().scale()).isEqualTo(2);
    }

    @Test
    void rejectsAnAmountTooPreciseForItsCurrencyRatherThanRoundingIt() {
        // Silently rounding money is worse than refusing it: the caller never learns the value it
        // stored is not the value it supplied.
        assertThatThrownBy(() -> Money.of("10.005", "USD"))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessageContaining("not valid");
        assertThatThrownBy(() -> Money.of("100.5", "JPY"))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.of("-1", "USD")).isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void rejectsAnUnknownCurrencyCode() {
        assertThatThrownBy(() -> Money.of("1", "ZZZ")).isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> new Money(null, Currency.getInstance("USD")))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void addsAndSubtractsWithinOneCurrency() {
        assertThat(Money.of("10.50", "USD").plus(Money.of("4.50", "USD")))
                .isEqualTo(Money.of("15.00", "USD"));
        assertThat(Money.of("10.50", "USD").minus(Money.of("0.50", "USD")))
                .isEqualTo(Money.of("10.00", "USD"));
    }

    @Test
    void refusesToCombineDifferentCurrenciesInsteadOfInventingAnExchangeRate() {
        Money usd = Money.of("10", "USD");
        Money myr = Money.of("10", "MYR");

        assertThatThrownBy(() -> usd.plus(myr))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> usd.isGreaterThan(myr))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void subtractionThatWouldGoNegativeFailsRatherThanProducingNegativeMoney() {
        Money small = Money.of("1", "USD");
        Money large = Money.of("2", "USD");

        assertThatThrownBy(() -> small.minus(large)).isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void exposesZeroAndCurrencyCode() {
        Money zero = Money.zero(Currency.getInstance("MYR"));

        assertThat(zero.isZero()).isTrue();
        assertThat(zero.currencyCode()).isEqualTo("MYR");
        assertThat(Money.of("0.01", "MYR").isZero()).isFalse();
    }
}
