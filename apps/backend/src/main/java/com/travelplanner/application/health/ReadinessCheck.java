package com.travelplanner.application.health;

/**
 * Outcome of a single {@link ReadinessContributor}.
 *
 * @param ready  whether this dependency is usable
 * @param detail short diagnostic shown when not ready; never a stack trace or credential
 */
public record ReadinessCheck(boolean ready, String detail) {

    public static ReadinessCheck up() {
        return new ReadinessCheck(true, null);
    }

    public static ReadinessCheck down(String detail) {
        return new ReadinessCheck(false, detail);
    }
}
