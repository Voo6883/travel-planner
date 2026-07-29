package com.travelplanner.domain.port;

import com.travelplanner.domain.model.Trip;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link Trip}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p><strong>Every read takes the owner's id.</strong> There is no {@code findById(tripId)} on this
 * port, and that omission is the point: PLAN §4.0.2-L requires every query to filter by the
 * {@code user_id} from {@link com.travelplanner.domain.valueobject.UserContext}, and a port that
 * offers an unscoped lookup makes cross-user leakage a one-line mistake in a service somebody
 * writes six tasks from now. A missing row and a row owned by somebody else are deliberately
 * indistinguishable to the caller — both are {@link Optional#empty()}, so a 404 cannot be used to
 * probe for the existence of another user's trip.
 *
 * <p>Paging is not here yet. {@code application/page/PageQuery} now lives in the application layer
 * — task 15 moved it out of {@code api/dto/page}, where an ArchUnit rule caught the admin service
 * importing a web DTO. The domain still may not import it (PLAN §4.0.1: no outward imports), so
 * task 18, which owns the trip list, still decides how a page request reaches this port.
 */
public interface TripRepositoryPort {

    /**
     * Inserts or updates. On update, the persistence adapter enforces the JPA {@code @Version}
     * check and raises {@link com.travelplanner.domain.exception.VersionConflictException} when a
     * concurrent transaction won the race (ADR 008).
     */
    Trip save(Trip trip);

    Optional<Trip> findByIdAndUserId(UUID tripId, UUID userId);

    /** Newest first, matching {@code ix_trip_user_id_created_at}. */
    List<Trip> findAllByUserId(UUID userId);

    /** Removes the trip and, by cascade, its brief. Returns false when the caller is not the owner. */
    boolean deleteByIdAndUserId(UUID tripId, UUID userId);
}
