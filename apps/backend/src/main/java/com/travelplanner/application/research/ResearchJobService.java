package com.travelplanner.application.research;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ResearchJobNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.ResearchStatusTransition;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.port.ResearchJobRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C2 research orchestration — the durable state machine, not the agent (tasks/23, UC-C2-01/02).
 *
 * <h2>Start is persist-then-dispatch, never dispatch-then-hope</h2>
 *
 * <p>{@link #start} writes the {@code QUEUED} job and moves the trip to {@code RESEARCH_QUEUED} in
 * one short transaction, then publishes {@link ResearchJobQueuedEvent}. The executor listens for
 * that event {@code AFTER_COMMIT}, so a worker is handed a job only once the row is durable — the
 * task's "persist state before dispatch". The HTTP call returns {@code 202} immediately and never
 * holds the connection for the run (the task's Do-not list).
 *
 * <h2>The lifecycle transitions run with no session</h2>
 *
 * <p>{@link #beginRunning}, {@link #reportProgress}, {@link #markCompleted}, and {@link #failJob}
 * are called by the background worker and the reconciler, which have no {@link UserContext}. They
 * operate on a job by id and reach the owning trip through {@link ResearchJob#userId()} and the same
 * user-scoped port every request uses — the reason the job carries its owner. Each is a separate
 * proxied method so its {@code @TransactionalWrite} boundary is real (Spring's proxying ignores
 * self-invocation), and each is idempotent against a stale view: a job that is no longer in the
 * expected state is left alone rather than forced.
 *
 * <h2>Failure never invents a trip status</h2>
 *
 * <p>There is no trip-level "failed" ({@code docs/PLAN-COMPATIBILITY.md}, the task's Do-not list).
 * A failed run marks the <em>job</em> {@code FAILED} with an {@code error_code} and returns the trip
 * to {@code BRIEF_COMPLETE}, the last state a re-run can start from (UC-C2-07).
 */
@Service
@RequiresDatabase
public class ResearchJobService {

    private static final Logger log = LoggerFactory.getLogger(ResearchJobService.class);

    private final TripAccess access;
    private final TripRepositoryPort trips;
    private final ResearchJobRepositoryPort jobs;
    private final ResearchCompletionHook completionHook;
    private final ApplicationEventPublisher events;

    public ResearchJobService(TripAccess access, TripRepositoryPort trips,
            ResearchJobRepositoryPort jobs, ResearchCompletionHook completionHook,
            ApplicationEventPublisher events) {
        this.access = access;
        this.trips = trips;
        this.jobs = jobs;
        this.completionHook = completionHook;
        this.events = events;
    }

    /**
     * Starts a research run (UC-C2-01). Requires the trip to be the caller's and
     * {@code BRIEF_COMPLETE}, and refuses a second active job.
     *
     * @throws com.travelplanner.domain.exception.TripNotFoundException when the trip is not the
     *         caller's or does not exist
     * @throws ValidationFailedException when the trip is not {@code BRIEF_COMPLETE}, or a job is
     *         already active for it
     */
    @TransactionalWrite
    public ResearchJobView start(StartResearchCommand command, UserContext user) {
        Trip trip = access.requireOwned(command.tripId(), user);
        TripStatus target = ResearchStatusTransition.requireStart(trip.status());
        jobs.findActiveByTripId(trip.id()).ifPresent(active -> {
            throw ValidationFailedException.field("status",
                    "a research job is already active for this trip");
        });

        Instant now = Instant.now();
        ResearchJob queued = jobs.save(ResearchJob.queue(trip.id(), user.userId(), now));
        trips.save(trip.withStatus(target, now));
        events.publishEvent(new ResearchJobQueuedEvent(queued.id()));
        log.info("research_job_started job={} trip={} user={}",
                queued.id(), trip.id(), user.userId());
        return ResearchJobView.of(queued);
    }

    /**
     * The poll (UC-C2-02). Ownership is proven on the trip first, so a job id that is not this
     * trip's is a {@code 404} indistinguishable from one that never existed.
     *
     * @throws com.travelplanner.domain.exception.TripNotFoundException when the trip is not the caller's
     * @throws ResearchJobNotFoundException when no such job belongs to the trip
     */
    @Transactional(readOnly = true)
    public ResearchJobView get(UUID tripId, UUID jobId, UserContext user) {
        Trip trip = access.requireOwned(tripId, user);
        return jobs.findByIdAndTripId(jobId, trip.id())
                .map(ResearchJobView::of)
                .orElseThrow(ResearchJobNotFoundException::new);
    }

    /**
     * {@code QUEUED → RUNNING}, moving the trip to {@code RESEARCH_RUNNING}. Called by the worker.
     *
     * @return the now-running job when it was queued and has been claimed; empty if it was gone or
     *         already past queued (a duplicate dispatch, which must be a no-op)
     */
    @TransactionalWrite
    public Optional<ResearchJob> beginRunning(UUID jobId) {
        ResearchJob job = jobs.findById(jobId).orElse(null);
        if (job == null || job.status() != ResearchJobStatus.QUEUED) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        ResearchJob running = jobs.save(job.markRunning(now));
        moveTrip(job, now, ResearchStatusTransition::canBeginRunning, TripStatus.RESEARCH_RUNNING);
        log.info("research_job_running job={} trip={} user={}", jobId, job.tripId(), job.userId());
        return Optional.of(running);
    }

    /** Records advisory progress while running. A no-op once the job leaves {@code RUNNING}. */
    @TransactionalWrite
    public void reportProgress(UUID jobId, int progressPct) {
        jobs.findById(jobId)
                .filter(job -> job.status() == ResearchJobStatus.RUNNING)
                .ifPresent(job -> jobs.save(job.markProgress(clampProgress(progressPct), Instant.now())));
    }

    /**
     * {@code RUNNING → COMPLETED}, then {@code trip → RESEARCH_READY} through
     * {@link ResearchCompletionHook} in the same transaction. A no-op if the job already left
     * {@code RUNNING} (e.g. a late completion after a timeout already failed it).
     */
    @TransactionalWrite
    public void markCompleted(UUID jobId) {
        ResearchJob job = jobs.findById(jobId).orElse(null);
        if (job == null || job.status() != ResearchJobStatus.RUNNING) {
            return;
        }
        Instant now = Instant.now();
        jobs.save(job.complete(now));
        trips.findByIdAndUserId(job.tripId(), job.userId())
                .filter(trip -> ResearchStatusTransition.canBecomeReady(trip.status()))
                .ifPresent(trip -> {
                    completionHook.onResearchCompleted(job.id(), trip.id());
                    trips.save(trip.withStatus(TripStatus.RESEARCH_READY, now));
                    events.publishEvent(new ResearchReadyEvent(job.id(), trip.id(), job.userId()));
                });
        log.info("research_job_completed job={} trip={} user={}", jobId, job.tripId(), job.userId());
    }

    /**
     * {@code RUNNING → FAILED} with a typed code, recovering the trip to {@code BRIEF_COMPLETE}. A
     * no-op if the job already left {@code RUNNING}, so a timeout and a late error cannot both fail
     * the same run.
     */
    @TransactionalWrite
    public void failJob(UUID jobId, String errorCode) {
        ResearchJob job = jobs.findById(jobId).orElse(null);
        if (job == null || job.status() != ResearchJobStatus.RUNNING) {
            return;
        }
        Instant now = Instant.now();
        jobs.save(job.fail(errorCode, now));
        moveTrip(job, now, ResearchStatusTransition::canRecover, TripStatus.BRIEF_COMPLETE);
        log.warn("research_job_failed job={} trip={} user={} error_code={}",
                jobId, job.tripId(), job.userId(), errorCode);
    }

    private void moveTrip(ResearchJob job, Instant now,
            java.util.function.Predicate<TripStatus> guard, TripStatus target) {
        trips.findByIdAndUserId(job.tripId(), job.userId())
                .filter(trip -> guard.test(trip.status()))
                .ifPresent(trip -> trips.save(trip.withStatus(target, now)));
    }

    private static int clampProgress(int progressPct) {
        if (progressPct < ResearchJob.MIN_PROGRESS) {
            return ResearchJob.MIN_PROGRESS;
        }
        return Math.min(progressPct, ResearchJob.MAX_PROGRESS);
    }
}
