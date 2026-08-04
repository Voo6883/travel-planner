package com.travelplanner.application.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.research.ResearchTestFakes.InMemoryJobs;
import com.travelplanner.application.research.ResearchTestFakes.InMemoryTrips;
import com.travelplanner.application.research.ResearchTestFakes.RecordingCompletionHook;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ResearchJobNotFoundException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The C2 durable state machine: start, poll, the session-less lifecycle transitions, ownership
 * scoping, the duplicate-active refusal, and re-run history — all against in-memory ports that keep
 * the optimistic lock and the one-active-job-per-trip guarantee the schema enforces.
 */
class ResearchJobServiceTest {

    private static final UserContext OWNER =
            UserContext.of(UUID.randomUUID(), "owner@example.com", Role.USER);
    private static final UserContext STRANGER =
            UserContext.of(UUID.randomUUID(), "stranger@example.com", Role.USER);
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    private InMemoryTrips trips;
    private InMemoryJobs jobs;
    private RecordingCompletionHook completionHook;
    private List<Object> events;
    private ResearchJobService service;

    @BeforeEach
    void setUp() {
        trips = new InMemoryTrips();
        jobs = new InMemoryJobs();
        completionHook = new RecordingCompletionHook();
        events = new ArrayList<>();
        service = new ResearchJobService(new TripAccess(trips), trips, jobs, completionHook,
                events::add);
    }

    private UUID seedTrip(TripStatus status) {
        Trip trip = new Trip(UUID.randomUUID(), OWNER.userId(), "Kyoto", status, null, 3, NOW, NOW);
        trips.seed(trip);
        return trip.id();
    }

    @Test
    void startQueuesAJobMovesTheTripAndPublishesADispatchEvent() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);

        ResearchJobView view = service.start(new StartResearchCommand(tripId), OWNER);

        assertThat(view.status()).isEqualTo(ResearchJobStatus.QUEUED);
        assertThat(view.tripId()).isEqualTo(tripId);
        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.RESEARCH_QUEUED);
        assertThat(jobs.stored(view.jobId()).status()).isEqualTo(ResearchJobStatus.QUEUED);
        assertThat(events).singleElement().isInstanceOf(ResearchJobQueuedEvent.class);
        assertThat(((ResearchJobQueuedEvent) events.get(0)).jobId()).isEqualTo(view.jobId());
    }

    @Test
    void startIsRefusedWhenTheTripIsNotBriefComplete() {
        UUID tripId = seedTrip(TripStatus.DRAFT);

        assertThatThrownBy(() -> service.start(new StartResearchCommand(tripId), OWNER))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(jobs.count()).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    void startIsRefusedWhenAJobIsAlreadyActiveForTheTrip() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        service.start(new StartResearchCommand(tripId), OWNER);
        // Model a concurrent second attempt: the trip is now RESEARCH_QUEUED, but even forcing it back
        // the active-job check refuses the second start.
        trips.seed(new Trip(tripId, OWNER.userId(), "Kyoto", TripStatus.BRIEF_COMPLETE, null,
                5, NOW, NOW));

        assertThatThrownBy(() -> service.start(new StartResearchCommand(tripId), OWNER))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(jobs.count()).isEqualTo(1);
    }

    @Test
    void anotherUsersTripCannotBeResearchedOrPolled() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);

        assertThatThrownBy(() -> service.start(new StartResearchCommand(tripId), STRANGER))
                .isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> service.get(tripId, UUID.randomUUID(), STRANGER))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void pollReturnsTheJobAndHidesOneThatIsNotThisTrips() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        ResearchJobView started = service.start(new StartResearchCommand(tripId), OWNER);

        assertThat(service.get(tripId, started.jobId(), OWNER).jobId()).isEqualTo(started.jobId());
        assertThatThrownBy(() -> service.get(tripId, UUID.randomUUID(), OWNER))
                .isInstanceOf(ResearchJobNotFoundException.class);
    }

    @Test
    void beginRunningClaimsAQueuedJobAndMovesTheTrip() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        ResearchJobView started = service.start(new StartResearchCommand(tripId), OWNER);

        Optional<ResearchJob> running = service.beginRunning(started.jobId());

        assertThat(running).isPresent();
        assertThat(running.get().status()).isEqualTo(ResearchJobStatus.RUNNING);
        assertThat(running.get().attempts()).isEqualTo(1);
        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.RESEARCH_RUNNING);
    }

    @Test
    void aDuplicateOrMissingBeginRunningIsANoOp() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        ResearchJobView started = service.start(new StartResearchCommand(tripId), OWNER);
        service.beginRunning(started.jobId());

        assertThat(service.beginRunning(started.jobId())).isEmpty();
        assertThat(service.beginRunning(UUID.randomUUID())).isEmpty();
    }

    @Test
    void completingARunningJobMovesTheTripReadyAndInvokesTheHook() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        ResearchJobView started = service.start(new StartResearchCommand(tripId), OWNER);
        service.beginRunning(started.jobId());

        service.markCompleted(started.jobId());

        assertThat(jobs.stored(started.jobId()).status()).isEqualTo(ResearchJobStatus.COMPLETED);
        assertThat(jobs.stored(started.jobId()).progressPct()).isEqualTo(100);
        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.RESEARCH_READY);
        assertThat(completionHook.completed()).containsExactly(started.jobId());
    }

    @Test
    void completingAJobThatIsNoLongerRunningIsANoOp() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        ResearchJobView started = service.start(new StartResearchCommand(tripId), OWNER);
        service.beginRunning(started.jobId());
        service.failJob(started.jobId(), "research_timeout");

        service.markCompleted(started.jobId());

        assertThat(jobs.stored(started.jobId()).status()).isEqualTo(ResearchJobStatus.FAILED);
        assertThat(completionHook.completed()).isEmpty();
    }

    @Test
    void failingARunningJobRecordsTheCodeAndRecoversTheTripToBriefComplete() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        ResearchJobView started = service.start(new StartResearchCommand(tripId), OWNER);
        service.beginRunning(started.jobId());

        service.failJob(started.jobId(), "research_failed");

        assertThat(jobs.stored(started.jobId()).status()).isEqualTo(ResearchJobStatus.FAILED);
        assertThat(jobs.stored(started.jobId()).errorCode()).isEqualTo("research_failed");
        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
    }

    @Test
    void progressIsRecordedWhileRunningAndIgnoredOnceTerminal() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        ResearchJobView started = service.start(new StartResearchCommand(tripId), OWNER);
        service.beginRunning(started.jobId());

        service.reportProgress(started.jobId(), 55);
        assertThat(jobs.stored(started.jobId()).progressPct()).isEqualTo(55);

        service.markCompleted(started.jobId());
        service.reportProgress(started.jobId(), 10);
        assertThat(jobs.stored(started.jobId()).progressPct()).isEqualTo(100);
    }

    @Test
    void aReRunAfterAFailedJobCreatesANewRowAndKeepsTheOldAsHistory() {
        UUID tripId = seedTrip(TripStatus.BRIEF_COMPLETE);
        ResearchJobView first = service.start(new StartResearchCommand(tripId), OWNER);
        service.beginRunning(first.jobId());
        service.failJob(first.jobId(), "research_failed");
        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.BRIEF_COMPLETE);

        ResearchJobView second = service.start(new StartResearchCommand(tripId), OWNER);

        assertThat(second.jobId()).isNotEqualTo(first.jobId());
        assertThat(jobs.count()).isEqualTo(2);
        assertThat(jobs.stored(first.jobId()).status()).isEqualTo(ResearchJobStatus.FAILED);
        assertThat(jobs.stored(second.jobId()).status()).isEqualTo(ResearchJobStatus.QUEUED);
    }
}
