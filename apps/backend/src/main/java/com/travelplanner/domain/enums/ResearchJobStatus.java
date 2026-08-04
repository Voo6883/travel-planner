package com.travelplanner.domain.enums;

import java.util.Locale;

/**
 * The lifecycle of one research run (PLAN C2, UC-C2-01/02).
 *
 * <p>The names are the persisted values ({@code research_job.status varchar} with a CHECK
 * constraint), and {@code MigrationContractTest} asserts the check lists exactly these constants —
 * the same precedent as {@link TripStatus}. The <em>wire</em> form is lower-case
 * ({@code queued|running|completed|failed}); {@link #wire()} performs that mapping, which happens at
 * the DTO boundary only so the database and the domain keep one canonical casing.
 *
 * <p>No transition logic lives here — {@link com.travelplanner.domain.model.ResearchJob} owns which
 * moves are legal, the same way {@code Trip} owns its own mutators.
 */
public enum ResearchJobStatus {

    /** Persisted, not yet picked up by a worker. */
    QUEUED,

    /** A worker is executing the run. */
    RUNNING,

    /** The run finished and produced a result (task 25 persists it). */
    COMPLETED,

    /** The run ended without a result; {@code error_code} says why. */
    FAILED;

    /** QUEUED or RUNNING — a job that still occupies the one active slot per trip. */
    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }

    /** COMPLETED or FAILED — a job that will never change again. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /** The lower-case wire value (PLAN/USE-CASES {@code queued|running|completed|failed}). */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
