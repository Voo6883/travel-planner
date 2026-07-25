package com.travelplanner.application.health;

/**
 * One participant in the readiness decision.
 *
 * <p>Task 02 ships only a process-level contributor. PostgreSQL, and later any external
 * dependency that must be reachable before the service accepts traffic, are added by
 * registering further implementations — {@code /api/v1/ready} itself does not change.
 * See tasks/04-docker-runtime.md and tasks/07-database-domain-foundation.md.
 */
public interface ReadinessContributor {

    /** Stable identifier reported in the readiness response, e.g. {@code "database"}. */
    String name();

    /** Evaluates this dependency. Implementations must not throw; return a failed check instead. */
    ReadinessCheck check();
}
