package com.travelplanner.domain.valueobject;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in a specific currency. The only money type in the system (PLAN §4.0.2-A).
 *
 * <p>{@code BigDecimal} plus {@link Currency}, never {@code double}. A budget of {@code 4000.10}
 * is not representable in binary floating point, and a travel product compares, sums, and displays
 * budgets constantly — the error would surface as a penny that appears and disappears between
 * screens. The database column is {@code numeric} for the same reason.
 *
 * <p><strong>Scale is normalised on construction</strong> to the currency's minor-unit count (2 for
 * USD, 0 for JPY). This matters because {@code BigDecimal.equals} compares scale:
 * {@code 10} and {@code 10.00} are unequal as {@code BigDecimal}, and a record's generated
 * {@code equals} would inherit that. Normalising makes {@code Money.of("10", "USD")} equal to a
 * {@code Money} read back from a {@code numeric(19,4)} column.
 *
 * <p>Normalisation never rounds: an amount with more decimal places than the currency permits is a
 * rejected input, not a silently truncated one.
 */
public record Money(BigDecimal amount, Currency currency) {

    /** Currencies without a minor unit (metals, funds) report this from the JDK. */
    private static final int NO_MINOR_UNIT = -1;

    public Money {
        if (amount == null || currency == null) {
            throw ValidationFailedException.field("money", "amount and currency are both required");
        }
        if (amount.signum() < 0) {
            // A negative budget, price, or total has no meaning anywhere in C1-C4. Refunds and
            // adjustments, if they ever exist, are a signed *transaction* concept — not a Money.
            throw ValidationFailedException.field("money", "must not be negative");
        }
        amount = normaliseScale(amount, currency);
    }

    /** @param currencyCode ISO 4217, e.g. {@code "USD"}, {@code "MYR"} */
    public static Money of(String amount, String currencyCode) {
        return new Money(parseAmount(amount), parseCurrency(currencyCode));
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /** Zero in the given currency — the identity element for {@link #plus(Money)}. */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        return new Money(amount.add(requireSameCurrency(other).amount), currency);
    }

    /**
     * @throws ValidationFailedException when the result would be negative — see the class note on
     *         why a negative {@code Money} does not exist
     */
    public Money minus(Money other) {
        return new Money(amount.subtract(requireSameCurrency(other).amount), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    /** @throws ValidationFailedException when the currencies differ */
    public boolean isGreaterThan(Money other) {
        return amount.compareTo(requireSameCurrency(other).amount) > 0;
    }

    /** ISO 4217 code, for wire and log use. */
    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    private Money requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            // Cross-currency arithmetic needs an exchange rate with a timestamp and a source. That
            // is a domain service nobody has specified, so the operation fails loudly instead of
            // inventing a rate of 1.
            throw ValidationFailedException.field("money",
                    "cannot combine " + currencyCode() + " with " + other.currencyCode());
        }
        return other;
    }

    private static BigDecimal normaliseScale(BigDecimal amount, Currency currency) {
        int minorUnits = currency.getDefaultFractionDigits();
        if (minorUnits == NO_MINOR_UNIT) {
            return amount.stripTrailingZeros();
        }
        try {
            return amount.setScale(minorUnits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException tooPrecise) {
            throw ValidationFailedException.field("money",
                    "has more decimal places than " + currency.getCurrencyCode() + " allows");
        }
    }

    private static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException | NullPointerException notANumber) {
            throw ValidationFailedException.field("money", "amount is not a decimal number");
        }
    }

    private static Currency parseCurrency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw ValidationFailedException.field("money", "unknown currency code");
        }
    }
}
