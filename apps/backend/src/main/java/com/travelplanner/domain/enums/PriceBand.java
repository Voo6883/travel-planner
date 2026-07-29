package com.travelplanner.domain.enums;

/**
 * An ordinal cost band.
 *
 * <p>Deliberately not an amount. Comparing a 2019 yen figure against a 2026 baht one is
 * meaningless, and the curated sources state bands rather than prices. Actual money lives in
 * {@code PriceObservation}, with its currency attached.
 */
public enum PriceBand {
    FREE,
    BUDGET,
    MODERATE,
    EXPENSIVE,
    LUXURY
}
