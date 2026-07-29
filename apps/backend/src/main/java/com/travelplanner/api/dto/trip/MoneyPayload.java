package com.travelplanner.api.dto.trip;

import com.travelplanner.domain.valueobject.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An amount and its currency, on the wire.
 *
 * <p><strong>{@code amount} is a string, not a JSON number.</strong> PLAN §4.0.2-A keeps money in
 * {@code BigDecimal} and {@code numeric} for the whole of its life precisely because binary
 * floating point cannot represent {@code 4000.10}; serialising it as a JSON number hands it to a
 * JavaScript client as a {@code double} and undoes that at the last hop. A decimal string survives
 * the round trip exactly, and {@link Money#of(String, String)} — which already exists for this —
 * rejects anything that is not a decimal number, so the parse is not an extra failure mode.
 *
 * <p>Validation of the value itself is the domain's: {@code Money} refuses a negative amount, an
 * unknown currency, and more decimal places than the currency has minor units. The annotations here
 * only stop an obviously malformed body before it reaches a value object.
 */
public record MoneyPayload(
        @NotBlank
        @Pattern(regexp = "^\\d+(\\.\\d+)?$", message = "must be a non-negative decimal number")
        String amount,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency) {

    /** @return null when the brief has no budget yet — an absent field, not a zero one */
    public static MoneyPayload from(Money money) {
        return money == null
                ? null
                : new MoneyPayload(money.amount().toPlainString(), money.currencyCode());
    }

    /** @throws com.travelplanner.domain.exception.ValidationFailedException on an invalid value */
    public Money toMoney() {
        return Money.of(amount, currency);
    }

    /** Null-tolerant, so a request that omits the budget does not need a branch at every call site. */
    public static Money toMoney(MoneyPayload payload) {
        return payload == null ? null : payload.toMoney();
    }
}
