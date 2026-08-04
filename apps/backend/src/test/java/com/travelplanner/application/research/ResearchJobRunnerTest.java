package com.travelplanner.application.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.research.ResearchTestFakes.InMemoryJobs;
import com.travelplanner.application.research.ResearchTestFakes.InMemoryTrips;
import com.travelplanner.application.research.ResearchTestFakes.RecordingCompletionHook;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.config.ResearchProperties;
import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The background orchestration: the runner drives one job to a terminal state, mapping the handler's
 * return, throw, and overrun to {@code COMPLETED}, {@code research_failed}, and {@code research_timeout}
 * on real threads — the same {@code Future.get(timeout)} path the platform uses in production.
 */
class ResearchJobRunnerTest {

    private static final UserContext OWNER =
            UserContext.of(UUID.randomUUID(), "owner@example.com", Role.USER);
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    private InMemoryTrips trips;
    private InMemoryJobs jobs;
    private ResearchJobService service;
    private ExecutorService timeoutExecutor;

    @BeforeEach
    void setUp() {
        trips = new InMemoryTrips();
        jobs = new InMemoryJobs();
        service = new ResearchJobService(new TripAccess(trips), trips, jobs,
                new RecordingCompletionHook(), event -> { });
        timeoutExecutor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        timeoutExecutor.shutdownNow();
    }

    private UUID seedQueuedJob() {
        Trip trip = new Trip(UUID.randomUUID(), OWNER.userId(), "Kyoto",
                TripStatus.RESEARCH_QUEUED, null, 3, NOW, NOW);
        trips.seed(trip);
        ResearchJob job = ResearchJob.queue(trip.id(), OWNER.userId(), NOW);
        jobs.seed(job);
        return job.id();
    }

    private ResearchJobRunner runner(ResearchJobHandler handler, long timeoutMs) {
        ResearchProperties properties = new ResearchProperties();
        properties.setJobTimeoutMs(timeoutMs);
        return new ResearchJobRunner(service, handler, timeoutExecutor, properties);
    }

    @Test
    void aHandlerThatReturnsCompletesTheJobAndReadiesTheTrip() {
        UUID jobId = seedQueuedJob();
        List<Integer> progress = new ArrayList<>();
        ResearchJobHandler handler = context -> {
            context.reportProgress(50);
            progress.add(50);
        };

        runner(handler, 5_000L).run(jobId);

        assertThat(jobs.stored(jobId).status()).isEqualTo(ResearchJobStatus.COMPLETED);
        assertThat(trips.stored(jobs.stored(jobId).tripId()).status())
                .isEqualTo(TripStatus.RESEARCH_READY);
        assertThat(progress).containsExactly(50);
    }

    @Test
    void aHandlerThatThrowsFailsTheJobWithResearchFailed() {
        UUID jobId = seedQueuedJob();
        ResearchJobHandler handler = context -> {
            throw new IllegalStateException("agent blew up");
        };

        runner(handler, 5_000L).run(jobId);

        assertThat(jobs.stored(jobId).status()).isEqualTo(ResearchJobStatus.FAILED);
        assertThat(jobs.stored(jobId).errorCode()).isEqualTo(ResearchJobRunner.FAILED_ERROR);
        assertThat(trips.stored(jobs.stored(jobId).tripId()).status())
                .isEqualTo(TripStatus.BRIEF_COMPLETE);
    }

    @Test
    void aHandlerThatOverrunsTheBudgetIsInterruptedAndTimesOut() throws InterruptedException {
        UUID jobId = seedQueuedJob();
        CountDownLatch interrupted = new CountDownLatch(1);
        ResearchJobHandler handler = context -> {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException expected) {
                interrupted.countDown();
                throw expected;
            }
        };

        runner(handler, 100L).run(jobId);

        assertThat(interrupted.await(5, TimeUnit.SECONDS))
                .describedAs("the handler thread must be interrupted on overrun")
                .isTrue();
        assertThat(jobs.stored(jobId).status()).isEqualTo(ResearchJobStatus.FAILED);
        assertThat(jobs.stored(jobId).errorCode()).isEqualTo(ResearchJobRunner.TIMEOUT_ERROR);
        assertThat(trips.stored(jobs.stored(jobId).tripId()).status())
                .isEqualTo(TripStatus.BRIEF_COMPLETE);
    }

    @Test
    void aDuplicateDispatchOfANonQueuedJobDoesNothing() {
        UUID jobId = seedQueuedJob();
        boolean[] handlerRan = { false };
        ResearchJobHandler handler = context -> handlerRan[0] = true;
        // Claim and complete the job first, so the second run finds it non-queued.
        service.beginRunning(jobId);
        service.markCompleted(jobId);

        runner(handler, 5_000L).run(jobId);

        assertThat(handlerRan[0]).isFalse();
        assertThat(jobs.stored(jobId).status()).isEqualTo(ResearchJobStatus.COMPLETED);
    }
}
