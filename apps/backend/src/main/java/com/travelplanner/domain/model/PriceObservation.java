package com.travelplanner.domain.model;

import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.domain.valueobject.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One monthly price figure for a destination — the only place in the TKB holding actual money
 * (PLAN §4.1.2, ADR 010 §6).
 *
 * <p>Everywhere else uses an ordinal {@code PriceBand}, because comparing a 2019 yen figure against
 * a 2026 baht one is meaningless. Here the amount is a {@link Money}, so the currency travels with
 * the number: a price without its currency is not a price, and a second money type would be the
 * first step towards one of them losing it.
 *
 * @param category free text ({@code HOTEL_NIGHT}, {@code MEAL_MID_RANGE}, {@code TRANSIT_DAY_PASS})
 *        rather than an enum. A closed vocabulary would need a migration every time curation learns
 *        a new category; {@code uq_price_history_observation} supplies the discipline instead
 * @param observedOn the first day of the month the figure describes, matching
 *        {@code ck_price_history_observed_on_first_of_month}. Every source quotes monthly averages,
 *        and normalising the day is what makes "one observation per month" enforceable at all —
 *        without it, the same month could be recorded twice under two different dates
 */
public record PriceObservation(
        UUID id,
        UUID destinationId,
        String category,
        Money amount,
        LocalDate observedOn,
        KnowledgeProvenance provenance) {

    public PriceObservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(destinationId, "destinationId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(observedOn, "observedOn");
        Objects.requireNonNull(provenance, "provenance");

        if (category.isBlank()) {
            throw new IllegalArgumentException("category must not be blank");
        }
        // ck_price_history_amount_positive. Money already refuses a negative amount; zero is the
        // remaining case, and a zero-cost hotel night is a failed import rather than a bargain.
        if (amount.isZero()) {
            throw new IllegalArgumentException("amount must be positive, got " + amount.amount());
        }
        if (observedOn.getDayOfMonth() != 1) {
            throw new IllegalArgumentException(
                    "observedOn must be the first of a month, got " + observedOn);
        }
    }
}
