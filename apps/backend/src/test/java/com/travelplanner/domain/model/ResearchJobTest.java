package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The {@link ResearchJob} state machine: the happy path, every refused move, and the column
 * invariants that mirror {@code V24__create_research_job.sql} so an illegal row cannot be built.
 */
class ResearchJobTest {

    private static final UUID TRIP = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void aFreshJobIsQueuedWithItsRunIdEqualToItsIdAndVersionZero() {
        ResearchJob job = ResearchJob.queue(TRIP, USER, NOW);

        assertThat(job.status()).isEqualTo(ResearchJobStatus.QUEUED);
        assertThat(job.researchRunId()).isEqualTo(job.id());
        assertThat(job.tripId()).isEqualTo(TRIP);
        assertThat(job.userId()).isEqualTo(USER);
        assertThat(job.version()).isZero();
        assertThat(job.attempts()).isZero();
        assertThat(job.progressPct()).isZero();
        assertThat(job.startedAt()).isNull();
        assertThat(job.completedAt()).isNull();
        assertThat(job.errorCode()).isNull();
    }

    @Test
    void theHappyPathIsQueuedThenRunningThenCompleted() {
        ResearchJob queued = ResearchJob.queue(TRIP, USER, NOW);

        ResearchJob running = queued.markRunning(NOW.plusSeconds(1));
        assertThat(running.status()).isEqualTo(ResearchJobStatus.RUNNING);
        assertThat(running.attempts()).isEqualTo(1);
        assertThat(running.startedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(running.completedAt()).isNull();

        ResearchJob completed = running.complete(NOW.plusSeconds(2));
        assertThat(completed.status()).isEqualTo(ResearchJobStatus.COMPLETED);
        assertThat(completed.progressPct()).isEqualTo(ResearchJob.MAX_PROGRESS);
        assertThat(completed.completedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(completed.startedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void aRunningJobCanFailWithATypedCodeAndKeepsItsStartedAt() {
        ResearchJob running = ResearchJob.queue(TRIP, USER, NOW).markRunning(NOW);

        ResearchJob failed = running.fail("research_timeout", NOW.plusSeconds(90));

        assertThat(failed.status()).isEqualTo(ResearchJobStatus.FAILED);
        assertThat(failed.errorCode()).isEqualTo("research_timeout");
        assertThat(failed.startedAt()).isEqualTo(NOW);
        assertThat(failed.completedAt()).isEqualTo(NOW.plusSeconds(90));
    }

    @Test
    void progressIsAdvisoryAndOnlyMovesWhileRunning() {
        ResearchJob running = ResearchJob.queue(TRIP, USER, NOW).markRunning(NOW);

        ResearchJob progressed = running.markProgress(42, NOW.plusSeconds(1));

        assertThat(progressed.progressPct()).isEqualTo(42);
        assertThat(progressed.status()).isEqualTo(ResearchJobStatus.RUNNING);
    }

    @Test
    void everyIllegalTransitionIsRefusedRatherThanAbsorbed() {
        ResearchJob queued = ResearchJob.queue(TRIP, USER, NOW);
        ResearchJob completed = queued.markRunning(NOW).complete(NOW);

        // A queued job cannot complete, fail, or report progress — only start.
        assertThatThrownBy(() -> queued.complete(NOW)).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> queued.fail("x", NOW)).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> queued.markProgress(1, NOW))
                .isInstanceOf(ValidationFailedException.class);
        // A terminal job cannot be restarted or moved again.
        assertThatThrownBy(() -> completed.markRunning(NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> completed.complete(NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> completed.fail("x", NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void failingWithoutACodeIsRefused() {
        ResearchJob running = ResearchJob.queue(TRIP, USER, NOW).markRunning(NOW);

        assertThatThrownBy(() -> running.fail("  ", NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void theConstructorMirrorsTheSchemaCheckConstraints() {
        // error_code only on FAILED.
        assertThatThrownBy(() -> job(ResearchJobStatus.QUEUED, 0, "boom", null, null))
                .isInstanceOf(ValidationFailedException.class);
        // started_at set exactly once past QUEUED.
        assertThatThrownBy(() -> job(ResearchJobStatus.RUNNING, 0, null, null, null))
                .isInstanceOf(ValidationFailedException.class);
        // completed_at set exactly when terminal.
        assertThatThrownBy(() -> job(ResearchJobStatus.COMPLETED, 100, null, NOW, null))
                .isInstanceOf(ValidationFailedException.class);
        // progress out of range.
        assertThatThrownBy(() -> job(ResearchJobStatus.RUNNING, 101, null, NOW, null))
                .isInstanceOf(ValidationFailedException.class);
    }

    private static ResearchJob job(ResearchJobStatus status, int progress, String errorCode,
            Instant startedAt, Instant completedAt) {
        UUID id = UUID.randomUUID();
        return new ResearchJob(id, TRIP, USER, id, status, progress, errorCode, 0,
                startedAt, completedAt, 0, NOW, NOW);
    }
}
