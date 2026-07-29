package com.travelplanner.application.trip;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.Versioned;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The trip aggregate's lifecycle — list, read, create, rename, archive, delete (PLAN §3.1).
 *
 * <h2>Every method is user-scoped</h2>
 *
 * <p>PLAN §4.0.2-L. The scoping is not a filter this class remembers to apply: it is structural,
 * because {@link TripRepositoryPort} publishes no unscoped lookup and {@link TripAccess} is the
 * only way in. A trip owned by somebody else is indistinguishable here from one that does not
 * exist, in every method, including {@link #delete}.
 *
 * <h2>Creating a trip creates its brief</h2>
 *
 * <p>One transaction, two rows, in the documented lock order ({@code trip} then
 * {@code trip_brief}). A trip whose brief row is missing would make every later read a
 * "does it exist yet?" branch in three services, and the row costs nothing — an empty brief is what
 * a {@code DRAFT} trip means.
 *
 * <h2>Status is not writable here</h2>
 *
 * <p>{@code trip.status} is derived from brief completeness by {@link TripBriefService} and moved
 * to {@code ARCHIVED} by {@link #archive}. There is deliberately no method that takes a status: one
 * would let a client declare {@code BRIEF_COMPLETE} over an empty brief and step past the gate
 * PLAN §3.1 puts in front of C2.
 */
@Service
@RequiresDatabase
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripAccess access;
    private final TripRepositoryPort trips;
    private final TripBriefRepositoryPort briefs;

    public TripService(TripAccess access, TripRepositoryPort trips, TripBriefRepositoryPort briefs) {
        this.access = access;
        this.trips = trips;
        this.briefs = briefs;
    }

    /** Newest first. Not paginated: a single user's trip list is a screen, not a corpus. */
    @Transactional(readOnly = true)
    public List<Trip> list(UserContext user) {
        return trips.findAllByUserId(user.userId());
    }

    /** @throws TripNotFoundException when the trip does not exist or is not the caller's */
    @Transactional(readOnly = true)
    public Trip get(UUID tripId, UserContext user) {
        return access.requireOwned(tripId, user);
    }

    /** A {@code DRAFT} trip and the empty brief that goes with it. */
    @TransactionalWrite
    public Trip create(CreateTripCommand command, UserContext user) {
        Instant now = Instant.now();
        Trip created = trips.save(Trip.create(user.userId(), command.name(), now));
        briefs.save(TripBrief.createFor(created.id(), now));
        log.info("trip_created trip={} user={}", created.id(), user.userId());
        return created;
    }

    /**
     * Renames the trip.
     *
     * @throws com.travelplanner.domain.exception.VersionConflictException when
     *         {@code expectedVersion} is not the stored version — ADR 008 §2, carrying
     *         {@code details.current_version} so the loser can re-read rather than blindly refetch
     * @throws com.travelplanner.domain.exception.ValidationFailedException when the trip is
     *         archived, or the name is blank or too long
     */
    @TransactionalWrite
    public Trip rename(RenameTripCommand command, UserContext user) {
        Trip trip = access.requireEditable(command.tripId(), user);
        Versioned.requireVersion(trip, command.expectedVersion());
        return trips.save(trip.rename(command.name(), Instant.now()));
    }

    /**
     * Moves the trip to {@code ARCHIVED}, after which it is read-only for every actor.
     *
     * <p>{@link TripAccess#requireEditable} refuses an already-archived trip, so this is not
     * idempotent — repeating it is {@code validation_failed} rather than a silent success. That is
     * the honest answer: the second call is a client acting on a stale view, and ADR 008's whole
     * position is that such a client should be told.
     */
    @TransactionalWrite
    public Trip archive(ArchiveTripCommand command, UserContext user) {
        Trip trip = access.requireEditable(command.tripId(), user);
        Versioned.requireVersion(trip, command.expectedVersion());
        log.info("trip_archived trip={} user={}", trip.id(), user.userId());
        return trips.save(trip.withStatus(TripStatus.ARCHIVED, Instant.now()));
    }

    /**
     * Removes the trip and, by {@code ON DELETE CASCADE}, its brief.
     *
     * <p><strong>No {@code expected_version}.</strong> ADR 008 versions writes that are
     * <em>based on</em> prior content, so that the loser of a race can re-read and re-apply its
     * edit. A delete is based on nothing and has nothing to re-apply: the caller's intent is "this
     * trip should not exist", and that intent does not become wrong because the agent changed a
     * field a moment earlier. Attaching a version would also mean a request body on {@code DELETE},
     * which intermediaries are entitled to drop.
     *
     * @throws TripNotFoundException when the trip does not exist or is not the caller's — the same
     *         answer for both, so a delete cannot be used to probe for another user's trip
     */
    @TransactionalWrite
    public void delete(UUID tripId, UserContext user) {
        if (!trips.deleteByIdAndUserId(tripId, user.userId())) {
            throw new TripNotFoundException();
        }
        log.info("trip_deleted trip={} user={}", tripId, user.userId());
    }
}
