package com.travelplanner.api.dto.research;

import com.travelplanner.application.research.ResearchJobView;
import java.time.Instant;
import java.util.UUID;

/**
 * The research job as every research endpoint returns it (UC-C2-01/02).
 *
 * <p>{@code status} is the <strong>lower-case wire form</strong> ({@code queued|running|completed|
 * failed}) — a {@code String}, produced from {@link com.travelplanner.domain.enums.ResearchJobStatus}
 * at this boundary, so the database and domain keep their UPPER_SNAKE casing while the contract
 * publishes the vocabulary USE-CASES §C2 specifies.
 *
 * <p>The {@code 202} start response and the {@code 200} poll response share this shape: the start
 * body carries {@code job_id} and {@code status: queued}, and everything else a poll adds is simply
 * absent or zero at that moment. {@code error_code}, {@code started_at}, and {@code completed_at} are
 * null until the run reaches them.
 *
 * <p>Field names are camelCase and serialise to snake_case through the global naming strategy.
 */
public record ResearchJobResponse(
        UUID jobId,
        UUID tripId,
        String status,
        int progressPct,
        String errorCode,
        int attempts,
        Instant startedAt,
        Instant completedAt) {

    public static ResearchJobResponse from(ResearchJobView view) {
        return new ResearchJobResponse(view.jobId(), view.tripId(), view.status().wire(),
                view.progressPct(), view.errorCode(), view.attempts(),
                view.startedAt(), view.completedAt());
    }
}
