package com.travelplanner.domain.algorithm.ranking;

import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code w3·price_fit(price_history, brief.budget)} plus trip cost estimation from curated
 * categories {@code HOTEL_NIGHT}, {@code MEAL_MID_RANGE}, {@code TRANSIT_DAY_PASS}.
 */
final class PriceFitCalculator {

    static final double NEUTRAL = 0.5d;
    static final String HOTEL_NIGHT = "HOTEL_NIGHT";
    static final String MEAL_MID_RANGE = "MEAL_MID_RANGE";
    static final String TRANSIT_DAY_PASS = "TRANSIT_DAY_PASS";

    private PriceFitCalculator() {
    }

    static PriceSignal evaluate(DestinationCandidate candidate, TripBriefDetails brief) {
        Money budget = brief.budget();
        DateRange dates = brief.dates();
        if (budget == null || dates == null) {
            return PriceSignal.neutral();
        }
        Optional<Money> estimate = estimateCost(candidate, brief);
        if (estimate.isEmpty()) {
            return PriceSignal.missingData();
        }
        Money cost = estimate.get();
        if (!cost.currency().equals(budget.currency())) {
            return PriceSignal.currencyMismatch();
        }
        if (cost.isGreaterThan(budget)) {
            return PriceSignal.overBudget(cost);
        }
        return PriceSignal.ok(fitAgainstBudget(cost, budget), cost);
    }

    private static Optional<Money> estimateCost(DestinationCandidate candidate, TripBriefDetails brief) {
        Map<String, PriceObservation> latest = latestByCategory(candidate);
        PriceObservation hotel = latest.get(HOTEL_NIGHT);
        PriceObservation meal = latest.get(MEAL_MID_RANGE);
        PriceObservation transit = latest.get(TRANSIT_DAY_PASS);
        if (hotel == null || meal == null || transit == null) {
            return Optional.empty();
        }
        if (!sameCurrency(hotel, meal, transit)) {
            return Optional.empty();
        }
        DateRange dates = brief.dates();
        PartySize party = brief.party() == null ? PartySize.ofAdults(2) : brief.party();
        long nights = Math.max(dates.nights(), 1L);
        long days = dates.days();
        int rooms = roomsNeeded(party);
        Money total = scale(hotel.amount(), nights * rooms)
                .plus(scale(meal.amount(), days * party.total() * 2L))
                .plus(scale(transit.amount(), days * party.total()));
        return Optional.of(total);
    }

    private static Map<String, PriceObservation> latestByCategory(DestinationCandidate candidate) {
        Map<String, PriceObservation> latest = new HashMap<>();
        candidate.priceObservations().stream()
                .sorted(Comparator.comparing(PriceObservation::observedOn).reversed())
                .forEach(observation -> latest.putIfAbsent(observation.category(), observation));
        return latest;
    }

    private static boolean sameCurrency(PriceObservation a, PriceObservation b, PriceObservation c) {
        Currency currency = a.amount().currency();
        return currency.equals(b.amount().currency()) && currency.equals(c.amount().currency());
    }

    private static int roomsNeeded(PartySize party) {
        return Math.max(1, (int) Math.ceil(party.adults() / 2.0d));
    }

    private static Money scale(Money unit, long quantity) {
        BigDecimal scaled = unit.amount().multiply(BigDecimal.valueOf(quantity));
        return Money.of(scaled, unit.currency());
    }

    /** Under-budget fit: exact budget → 0.25; free trip → 1.0. */
    private static double fitAgainstBudget(Money cost, Money budget) {
        if (budget.isZero()) {
            return cost.isZero() ? 1.0d : 0.0d;
        }
        BigDecimal ratio = cost.amount().divide(budget.amount(), 8, RoundingMode.HALF_UP);
        double spent = ratio.doubleValue();
        return 0.25d + 0.75d * (1.0d - spent);
    }

    enum PriceOutcome {
        NEUTRAL,
        MISSING_DATA,
        CURRENCY_MISMATCH,
        OVER_BUDGET,
        OK
    }

    record PriceSignal(PriceOutcome outcome, double fit, Money estimatedCost) {

        static PriceSignal neutral() {
            return new PriceSignal(PriceOutcome.NEUTRAL, NEUTRAL, null);
        }

        static PriceSignal missingData() {
            return new PriceSignal(PriceOutcome.MISSING_DATA, NEUTRAL, null);
        }

        static PriceSignal currencyMismatch() {
            return new PriceSignal(PriceOutcome.CURRENCY_MISMATCH, 0.0d, null);
        }

        static PriceSignal overBudget(Money cost) {
            return new PriceSignal(PriceOutcome.OVER_BUDGET, 0.0d, cost);
        }

        static PriceSignal ok(double fit, Money cost) {
            return new PriceSignal(PriceOutcome.OK, fit, cost);
        }
    }
}
