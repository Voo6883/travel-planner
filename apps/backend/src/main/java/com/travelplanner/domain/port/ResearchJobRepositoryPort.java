package com.travelplanner.domain.port;

import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.model.ResearchJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link ResearchJob}. Implemented in {@code infrastructure/persistence/}.
 *
 * <p>The reads split into two audiences. The user-facing poll ({@link #findByIdAndTripId}) is
 * scoped by trip, and the service only reaches it after {@code TripAccess} has proved the trip is
 * the caller's — the same user-scoping discipline as {@link TripRepositoryPort}. The remaining
 * finders serve the <em>background worker and the startup reconciler</em>, which act with no
 * {@code UserContext} at all: they operate on a job by its id, which the platform itself minted and
 * dispatched, and recover the owning trip through {@link ResearchJob#userId()}.
 */
public interface ResearchJobRepositoryPort {

    /**
     * Inserts or updates. On update, the adapter enforces the JPA {@code @Version} check and raises
     * {@link com.travelplanner.domain.exception.VersionConflictException} on a concurrent write
     * (ADR 008); on insert of a second active job for a trip it raises the typed
     * {@code validation_failed} the {@code uq_research_job_active_per_trip} index guarantees.
     */
    ResearchJob save(ResearchJob job);

    /** A job by its own id, for the background worker that was handed exactly that id. */
    Optional<ResearchJob> findById(UUID jobId);

    /** The user-facing poll: the job, but only within the trip the caller was already shown to own. */
    Optional<ResearchJob> findByIdAndTripId(UUID jobId, UUID tripId);

    /**
     * The trip's current active ({@code QUEUED} or {@code RUNNING}) job, if any — the friendly
     * duplicate-start check. The {@code uq_research_job_active_per_trip} index is the real backstop.
     */
    Optional<ResearchJob> findActiveByTripId(UUID tripId);

    /**
     * The trip's newest job of any status — chat progress after the active window closes
     * (task 27 {@code get_research_status}).
     */
    Optional<ResearchJob> findLatestByTripId(UUID tripId);

    /** Every job in a status, for the startup reconciler. */
    List<ResearchJob> findByStatus(ResearchJobStatus status);
}
