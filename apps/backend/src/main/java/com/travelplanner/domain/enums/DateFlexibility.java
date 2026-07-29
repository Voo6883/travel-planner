package com.travelplanner.domain.enums;

/**
 * How much the traveller's dates can move — the {@code date_flexibility} answer UC-C1-04 names.
 *
 * <p>Kept separate from {@link com.travelplanner.domain.valueobject.DateRange} rather than being
 * modelled as a widened range. The two facts are different: a range says which days were entered,
 * flexibility says how much C2 may shift them when a cheaper week or a better season sits next
 * door. Encoding "flexible" as a wider range would lose the original intent and make
 * {@code seasonality_fit} score a window the user never asked for.
 *
 * <p>The names are the wire values and the persisted values, exactly as the {@code choice} options
 * in {@code plans/USE-CASES.md} UC-C1-04 spell them (uppercased, per PLAN §13's enum convention).
 */
public enum DateFlexibility {

    /** The dates are booked around something immovable. C2 may not shift them. */
    FIXED,

    /** The trip may move by up to a week in either direction. */
    FLEXIBLE_WEEK,

    /** The trip may move anywhere inside the surrounding month. */
    FLEXIBLE_MONTH
}
