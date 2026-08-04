package com.travelplanner.application.research;

import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.port.ResearchJobRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory ports for the research use cases, no Spring and no database.
 *
 * <p>Like {@code TripTestFakes}, the fakes reproduce JPA's {@code @Version} semantics <em>and</em>
 * the two database guarantees the real adapter translates — the {@code uq_research_job_active_per_trip}
 * partial unique index and the optimistic lock — because a fake that ignored either would let a test
 * pass against a repository that had neither, which is the exact failure the schema exists to stop.
 */
final class ResearchTestFakes {

    private ResearchTestFakes() {
    }

    /** {@link TripRepositoryPort} over a map, with the ADR 008 optimistic lock modelled. */
    static final class InMemoryTrips implements TripRepositoryPort {

        private final Map<UUID, Trip> rows = new LinkedHashMap<>();

        @Override
        public Trip save(Trip trip) {
            Trip stored = rows.get(trip.id());
            if (stored != null && stored.version() != trip.version()) {
                throw new VersionConflictException(stored.version());
            }
            Trip persisted = new Trip(trip.id(), trip.userId(), trip.name(), trip.status(),
                    trip.selectedRecommendationId(), trip.version() + 1, trip.createdAt(),
                    trip.updatedAt());
            rows.put(persisted.id(), persisted);
            return persisted;
        }

        @Override
        public Optional<Trip> findByIdAndUserId(UUID tripId, UUID userId) {
            return Optional.ofNullable(rows.get(tripId)).filter(trip -> trip.isOwnedBy(userId));
        }

        @Override
        public List<Trip> findAllByUserId(UUID userId) {
            return rows.values().stream().filter(trip -> trip.isOwnedBy(userId)).toList();
        }

        @Override
        public boolean deleteByIdAndUserId(UUID tripId, UUID userId) {
            return findByIdAndUserId(tripId, userId)
                    .map(trip -> rows.remove(trip.id()) != null)
                    .orElse(false);
        }

        void seed(Trip trip) {
            rows.put(trip.id(), trip);
        }

        Trip stored(UUID tripId) {
            return rows.get(tripId);
        }
    }

    /**
     * {@link ResearchJobRepositoryPort} over a map, with the optimistic lock and the
     * one-active-job-per-trip unique index both modelled.
     */
    static final class InMemoryJobs implements ResearchJobRepositoryPort {

        private final Map<UUID, ResearchJob> rows = new LinkedHashMap<>();

        @Override
        public ResearchJob save(ResearchJob job) {
            ResearchJob stored = rows.get(job.id());
            if (stored != null && stored.version() != job.version()) {
                throw new VersionConflictException(stored.version());
            }
            if (job.status().isActive()) {
                rows.values().stream()
                        .filter(other -> !other.id().equals(job.id()))
                        .filter(other -> other.tripId().equals(job.tripId()))
                        .filter(other -> other.status().isActive())
                        .findAny()
                        .ifPresent(conflict -> {
                            throw ValidationFailedException.field("status",
                                    "a research job is already active for this trip");
                        });
            }
            ResearchJob persisted = new ResearchJob(job.id(), job.tripId(), job.userId(),
                    job.researchRunId(), job.status(), job.progressPct(), job.errorCode(),
                    job.attempts(), job.startedAt(), job.completedAt(), job.version() + 1,
                    job.createdAt(), job.updatedAt());
            rows.put(persisted.id(), persisted);
            return persisted;
        }

        @Override
        public Optional<ResearchJob> findById(UUID jobId) {
            return Optional.ofNullable(rows.get(jobId));
        }

        @Override
        public Optional<ResearchJob> findByIdAndTripId(UUID jobId, UUID tripId) {
            return findById(jobId).filter(job -> job.tripId().equals(tripId));
        }

        @Override
        public Optional<ResearchJob> findActiveByTripId(UUID tripId) {
            return rows.values().stream()
                    .filter(job -> job.tripId().equals(tripId))
                    .filter(job -> job.status().isActive())
                    .max(Comparator.comparing(ResearchJob::createdAt));
        }

        @Override
        public List<ResearchJob> findByStatus(ResearchJobStatus status) {
            return new ArrayList<>(rows.values().stream()
                    .filter(job -> job.status() == status)
                    .toList());
        }

        void seed(ResearchJob job) {
            rows.put(job.id(), job);
        }

        ResearchJob stored(UUID jobId) {
            return rows.get(jobId);
        }

        int count() {
            return rows.size();
        }
    }

    /** Records the jobs dispatched to a worker, without running anything. */
    static final class RecordingExecutor implements ResearchJobExecutor {

        private final List<UUID> enqueued = new ArrayList<>();

        @Override
        public void enqueue(UUID jobId) {
            enqueued.add(jobId);
        }

        List<UUID> enqueued() {
            return enqueued;
        }
    }

    /** Records which jobs the completion hook was invoked for, in order. */
    static final class RecordingCompletionHook implements ResearchCompletionHook {

        private final List<UUID> completed = new ArrayList<>();

        @Override
        public void onResearchCompleted(UUID jobId, UUID tripId) {
            completed.add(jobId);
        }

        List<UUID> completed() {
            return completed;
        }
    }
}
