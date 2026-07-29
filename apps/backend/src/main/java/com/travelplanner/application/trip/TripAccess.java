package com.travelplanner.application.trip;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The ownership gate every trip-scoped use case starts at (PLAN §4.0.2-L).
 *
 * <p>It exists as its own bean rather than as a private helper on each service because both
 * {@link TripService} and {@link TripBriefService} need the identical two checks, and two copies of
 * an authorisation rule is one copy too many — the second one is where the {@code userId} filter
 * eventually gets dropped for a "quick" internal call.
 *
 * <p>Not {@code @Transactional}. Every caller is already inside a transaction the service opened,
 * and the load here is the first row of the documented lock order ({@code trip} then
 * {@code trip_brief}), so it must join that transaction rather than start one beside it.
 */
@Service
@RequiresDatabase
public class TripAccess {

    private final TripRepositoryPort trips;

    public TripAccess(TripRepositoryPort trips) {
        this.trips = trips;
    }

    /**
     * The caller's trip.
     *
     * @throws TripNotFoundException when no trip has this id <em>or</em> when it belongs to someone
     *         else. The port cannot tell the two apart and deliberately does not try — see the
     *         exception's own note on why a 403 here would be an enumeration oracle
     */
    public Trip requireOwned(UUID tripId, UserContext user) {
        return trips.findByIdAndUserId(tripId, user.userId())
                .orElseThrow(TripNotFoundException::new);
    }

    /**
     * The caller's trip, refused if it is archived.
     *
     * <p>Archived is read-only for <em>every</em> actor, the agent included (PLAN §3.1, "ARCHIVED —
     * view only, no mutations"). {@link Trip} enforces the same rule on its own mutators, so a
     * write that skipped this check would still fail; the check is here so the refusal happens
     * before anything is loaded or computed, and so the message names the trip rather than the
     * field the write happened to touch first.
     */
    public Trip requireEditable(UUID tripId, UserContext user) {
        Trip trip = requireOwned(tripId, user);
        if (trip.status().isReadOnly()) {
            throw ValidationFailedException.field("status",
                    "an archived trip cannot be modified");
        }
        return trip;
    }
}
