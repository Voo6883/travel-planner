package com.travelplanner.application.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.research.ResearchTestFakes.InMemoryJobs;
import com.travelplanner.application.research.ResearchTestFakes.InMemoryTrips;
import com.travelplanner.application.research.ResearchTestFakes.RecordingCompletionHook;
import com.travelplanner.application.research.ResearchTestFakes.RecordingExecutor;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.Trip;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Restart recovery. At boot a {@code RUNNING} job is orphaned by definition on a single node — its
 * worker died — so it is failed and its trip recovered, while a {@code QUEUED} job that never
 * dispatched is simply re-enqueued. Terminal jobs are left untouched.
 */
class ResearchStaleJobReconcilerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    private InMemoryTrips trips;
    private InMemoryJobs jobs;
    private RecordingExecutor executor;
    private ResearchStaleJobReconciler reconciler;

    @BeforeEach
    void setUp() {
        trips = new InMemoryTrips();
        jobs = new InMemoryJobs();
        executor = new RecordingExecutor();
        ResearchJobService service = new ResearchJobService(new TripAccess(trips), trips, jobs,
                new RecordingCompletionHook(), event -> { });
        reconciler = new ResearchStaleJobReconciler(jobs, service, executor);
    }

    private UUID seedTrip(TripStatus status) {
        Trip trip = new Trip(UUID.randomUUID(), USER, "Kyoto", status, null, 3, NOW, NOW);
        trips.seed(trip);
        return trip.id();
    }

    @Test
    void anOrphanedRunningJobIsFailedAndItsTripRecovered() {
        UUID tripId = seedTrip(TripStatus.RESEARCH_RUNNING);
        ResearchJob running = ResearchJob.queue(tripId, USER, NOW).markRunning(NOW);
        jobs.seed(running);

        reconciler.reconcile();

        assertThat(jobs.stored(running.id()).status()).isEqualTo(ResearchJobStatus.FAILED);
        assertThat(jobs.stored(running.id()).errorCode())
                .isEqualTo(ResearchJobRunner.TIMEOUT_ERROR);
        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
    }

    @Test
    void anOrphanedQueuedJobIsReDispatched() {
        UUID tripId = seedTrip(TripStatus.RESEARCH_QUEUED);
        ResearchJob queued = ResearchJob.queue(tripId, USER, NOW);
        jobs.seed(queued);

        reconciler.reconcile();

        assertThat(executor.enqueued()).containsExactly(queued.id());
        assertThat(jobs.stored(queued.id()).status()).isEqualTo(ResearchJobStatus.QUEUED);
    }

    @Test
    void terminalJobsAreLeftUntouched() {
        UUID tripId = seedTrip(TripStatus.RESEARCH_READY);
        ResearchJob completed = ResearchJob.queue(tripId, USER, NOW).markRunning(NOW).complete(NOW);
        jobs.seed(completed);

        reconciler.reconcile();

        assertThat(jobs.stored(completed.id()).status()).isEqualTo(ResearchJobStatus.COMPLETED);
        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.RESEARCH_READY);
        assertThat(executor.enqueued()).isEmpty();
    }
}
