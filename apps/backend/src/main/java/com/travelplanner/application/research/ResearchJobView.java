package com.travelplanner.application.research;

import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.model.ResearchJob;
import java.time.Instant;
import java.util.UUID;

/**
 * What a caller may see of a research job — the poll payload (UC-C2-02), as an application view.
 *
 * <p>Carries the domain {@link ResearchJobStatus} rather than its wire string; the DTO boundary maps
 * it to the lower-case wire form. {@code errorCode}, {@code startedAt}, and {@code completedAt} are
 * nullable because a queued or running job has not reached them yet.
 */
public record ResearchJobView(
        UUID jobId,
        UUID tripId,
        ResearchJobStatus status,
        int progressPct,
        String errorCode,
        int attempts,
        Instant startedAt,
        Instant completedAt) {

    public static ResearchJobView of(ResearchJob job) {
        return new ResearchJobView(job.id(), job.tripId(), job.status(), job.progressPct(),
                job.errorCode(), job.attempts(), job.startedAt(), job.completedAt());
    }
}
