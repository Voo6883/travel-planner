package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One durable research run for a trip (PLAN C2, UC-C2-01/02, tasks/23).
 *
 * <p>Immutable, like {@link Trip}: every state change produces a new instance, which is what keeps
 * {@link #version()} trustworthy under the ADR 008 optimistic lock the background worker and the
 * startup reconciler both write through.
 *
 * <p><strong>The transitions are a small, refused-by-default state machine.</strong> A run goes
 * {@code QUEUED → RUNNING → COMPLETED} on the happy path, or {@code RUNNING → FAILED} when the work
 * throws or overruns its budget. Every other move — completing a queued job, failing a finished
 * one, reporting progress on a job that is not running — is a {@link ValidationFailedException},
 * because a background caller acting on a stale view is a bug to surface, not to absorb.
 *
 * <p>The column invariants in {@code V24__create_research_job.sql} are mirrored here so a job that
 * would violate the schema cannot be constructed in the first place: an {@code error_code} exists
 * exactly on a {@link ResearchJobStatus#FAILED} job, {@code startedAt} exactly once the run has
 * begun, and {@code completedAt} exactly once it is terminal.
 *
 * @param researchRunId the identity of this run, handed to task 25 so persisted recommendations can
 *        be attributed to it. Equal to {@link #id()} for a one-row-one-run job.
 */
public record ResearchJob(
        UUID id,
        UUID tripId,
        UUID userId,
        UUID researchRunId,
        ResearchJobStatus status,
        int progressPct,
        String errorCode,
        int attempts,
        Instant startedAt,
        Instant completedAt,
        int version,
        Instant createdAt,
        Instant updatedAt) implements Versioned {

    /** Progress is a percentage; the database CHECK enforces the same bound. */
    public static final int MIN_PROGRESS = 0;
    public static final int MAX_PROGRESS = 100;

    public ResearchJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tripId, "tripId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(researchRunId, "researchRunId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (progressPct < MIN_PROGRESS || progressPct > MAX_PROGRESS) {
            throw ValidationFailedException.field("progress_pct",
                    "must be between " + MIN_PROGRESS + " and " + MAX_PROGRESS);
        }
        if (version < 0) {
            throw ValidationFailedException.field("version", "must not be negative");
        }
        // Mirrors ck_research_job_error_code_only_when_failed.
        if ((errorCode != null) != (status == ResearchJobStatus.FAILED)) {
            throw ValidationFailedException.field("error_code",
                    "must be present exactly when the job has failed");
        }
        // Mirrors ck_research_job_started_at_matches.
        if ((startedAt != null) != (status != ResearchJobStatus.QUEUED)) {
            throw ValidationFailedException.field("started_at",
                    "must be present exactly once the job has started running");
        }
        // Mirrors ck_research_job_completed_at_matches_terminal.
        if ((completedAt != null) != status.isTerminal()) {
            throw ValidationFailedException.field("completed_at",
                    "must be present exactly when the job is terminal");
        }
    }

    /**
     * A brand-new {@code QUEUED} job. {@code researchRunId} equals the id — one row, one run — and
     * version 0 means "never persisted".
     */
    public static ResearchJob queue(UUID tripId, UUID userId, Instant now) {
        UUID id = UUID.randomUUID();
        return new ResearchJob(id, tripId, userId, id, ResearchJobStatus.QUEUED, 0, null, 0,
                null, null, 0, now, now);
    }

    /**
     * {@code QUEUED → RUNNING}. Stamps {@code startedAt} and counts the attempt.
     *
     * @throws ValidationFailedException when the job is not queued
     */
    public ResearchJob markRunning(Instant now) {
        requireStatus(ResearchJobStatus.QUEUED, "start");
        return new ResearchJob(id, tripId, userId, researchRunId, ResearchJobStatus.RUNNING,
                progressPct, null, attempts + 1, now, null, version, createdAt, now);
    }

    /**
     * Records coarse progress while {@code RUNNING}. Advisory only — it orders nothing.
     *
     * @throws ValidationFailedException when the job is not running, or the value is out of range
     */
    public ResearchJob markProgress(int newProgressPct, Instant now) {
        requireStatus(ResearchJobStatus.RUNNING, "report progress on");
        return new ResearchJob(id, tripId, userId, researchRunId, ResearchJobStatus.RUNNING,
                newProgressPct, null, attempts, startedAt, null, version, createdAt, now);
    }

    /**
     * {@code RUNNING → COMPLETED}. Forces progress to 100 — a completed run is wholly done.
     *
     * @throws ValidationFailedException when the job is not running
     */
    public ResearchJob complete(Instant now) {
        requireStatus(ResearchJobStatus.RUNNING, "complete");
        return new ResearchJob(id, tripId, userId, researchRunId, ResearchJobStatus.COMPLETED,
                MAX_PROGRESS, null, attempts, startedAt, now, version, createdAt, now);
    }

    /**
     * {@code RUNNING → FAILED}, carrying the typed reason.
     *
     * <p>Only a running job may fail: a queued job that never started is recovered by re-dispatch,
     * not by failure, which keeps {@code startedAt} honest (a failed run always ran).
     *
     * @throws ValidationFailedException when the job is not running, or the error code is blank
     */
    public ResearchJob fail(String failureErrorCode, Instant now) {
        requireStatus(ResearchJobStatus.RUNNING, "fail");
        if (failureErrorCode == null || failureErrorCode.isBlank()) {
            throw ValidationFailedException.field("error_code", "must not be blank on a failed job");
        }
        return new ResearchJob(id, tripId, userId, researchRunId, ResearchJobStatus.FAILED,
                progressPct, failureErrorCode.trim(), attempts, startedAt, now, version, createdAt, now);
    }

    /** Ownership check for the user-scoping rule (PLAN §4.0.2-L). */
    public boolean isOwnedBy(UUID candidateUserId) {
        return userId.equals(candidateUserId);
    }

    /** Absent on a failed job that carries no code — never, in practice, but typed as optional. */
    public Optional<String> errorCodeIfPresent() {
        return Optional.ofNullable(errorCode);
    }

    public Optional<Instant> startedAtIfPresent() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> completedAtIfPresent() {
        return Optional.ofNullable(completedAt);
    }

    private void requireStatus(ResearchJobStatus required, String action) {
        if (status != required) {
            throw ValidationFailedException.field("status",
                    "cannot " + action + " a job that is " + status);
        }
    }
}
