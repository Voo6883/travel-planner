package com.travelplanner.domain.enums;

/**
 * Why a globally-held app does not work in a country (tasks/17 "suppress inactive global
 * alternatives"; table {@code travel_app_replacement}, V21).
 *
 * <p>A category rather than free text, because the four cases need four different warnings and
 * collapsing them makes all four slightly wrong. "Uber does not operate here" and "Uber works, but
 * no driver will take the fare" are both true statements about China and only one of them is about
 * availability.
 *
 * <p>The names are the persisted values — {@code ck_travel_app_replacement_reason} lists exactly
 * these, and {@code MigrationContractTest} asserts the two agree, so adding a constant without
 * widening the constraint fails the build rather than the first insert.
 */
public enum AppReplacementReason {

    /** The service does not operate in this market at all. Opening the app shows an empty map. */
    NOT_AVAILABLE,

    /**
     * The app works elsewhere but is unreachable from inside this country's network. Distinct from
     * {@link #NOT_AVAILABLE} because the remedy differs: this one is sometimes solved by roaming on
     * a foreign SIM, and telling a traveller "not available" would hide that.
     */
    NETWORK_BLOCKED,

    /**
     * Installable and functional, but unusable without a local bank card, wallet, or phone number —
     * which is exactly what a visitor does not have. The most disappointing failure of the four,
     * because it is discovered at the payment screen.
     */
    NEEDS_LOCAL_PAYMENT,

    /**
     * It works, and nobody uses it. Coverage is thin, waits are long, or merchants do not accept it.
     * The recommendation is still to install the local app, but the global one is a fallback rather
     * than a dead end — so the UI must not say "does not work here".
     */
    NOT_THE_LOCAL_STANDARD;

    /**
     * Whether the replaced app is completely unusable, as opposed to merely a bad choice.
     *
     * <p>The distinction the UI needs: a hard failure is a warning, a soft one is advice. Rendering
     * {@link #NOT_THE_LOCAL_STANDARD} in the same red box as {@link #NETWORK_BLOCKED} would train
     * travellers to ignore the box.
     */
    public boolean isUnusable() {
        return this != NOT_THE_LOCAL_STANDARD;
    }
}
