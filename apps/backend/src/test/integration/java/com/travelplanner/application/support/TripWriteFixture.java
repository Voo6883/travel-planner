package com.travelplanner.application.support;

import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.Versioned;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * A minimal write use-case, existing only so the integration suite can exercise the real
 * {@link TransactionalWrite} template against a real database.
 *
 * <p>It lives in the {@code integrationTest} source set rather than in {@code main} on purpose. The
 * production trip and brief services belong to {@code tasks/18-trip-brief-core.md}; shipping a
 * half-designed one here to have something to test would be exactly the scope creep the harness
 * calls drift. What this task owes task 18 is a <em>proven</em> template, not a service.
 *
 * <p>Registered by {@code @Import} in the tests that need it, not by {@code @Service}. Component
 * scanning would otherwise add it to every integration context, including ones asserting what beans
 * exist.
 *
 * <p>Writes follow the documented lock order — {@code user → trip → trip_brief}.
 */
public class TripWriteFixture {

    private final UserRepositoryPort users;
    private final TripRepositoryPort trips;
    private final TripBriefRepositoryPort briefs;

    public TripWriteFixture(UserRepositoryPort users, TripRepositoryPort trips,
            TripBriefRepositoryPort briefs) {
        this.users = users;
        this.trips = trips;
        this.briefs = briefs;
    }

    /** Creates a trip and its brief in one transaction. Two tables, one boundary. */
    @TransactionalWrite
    public Trip createTripWithBrief(CreateTripCommand command) {
        return writeTripAndBrief(command);
    }

    /**
     * Identical, then throws a <em>checked</em> exception. Spring's default rollback rules would
     * commit this; {@code rollbackFor = Exception.class} is what makes both rows disappear.
     */
    @TransactionalWrite
    public Trip createTripWithBriefThenFail(CreateTripCommand command) throws FixtureFailure {
        writeTripAndBrief(command);
        throw new FixtureFailure("forced failure after both writes");
    }

    /**
     * The ADR 008 §2 write path: compare the version the caller edited against stored state before
     * mutating anything, so the loser gets {@code current_version} rather than a bare 409.
     */
    @TransactionalWrite
    public TripBrief updateBriefBudget(UpdateBriefBudgetCommand command) {
        TripBrief stored = briefs.findByTripId(command.tripId())
                .orElseThrow(() -> new IllegalStateException("no brief for " + command.tripId()));
        Versioned.requireVersion(stored, command.expectedVersion());
        return briefs.save(stored.withBudget(command.budget(), Instant.now()));
    }

    private Trip writeTripAndBrief(CreateTripCommand command) {
        Instant now = Instant.now();
        // Lock order starts at `user`: the ownership check is also the first row touched.
        if (!users.existsById(command.userId())) {
            throw new IllegalStateException("no such user " + command.userId());
        }
        Trip trip = trips.save(Trip.create(command.userId(), command.tripName(), now));
        briefs.save(TripBrief.createFor(trip.id(), now).withBudget(command.budget(), now));
        return trip;
    }

    /** Checked on purpose — the point of the rollback test. */
    public static class FixtureFailure extends Exception {

        private static final long serialVersionUID = 1L;

        public FixtureFailure(String message) {
            super(message);
        }
    }

    public record CreateTripCommand(UUID userId, String tripName, Money budget) {
    }

    public record UpdateBriefBudgetCommand(UUID tripId, int expectedVersion, Money budget) {
    }
}
