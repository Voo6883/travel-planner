package com.travelplanner.application.research;

import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * What a {@link ResearchJobHandler} is handed for one run (tasks/23, callback contract for task 25).
 *
 * <p>It carries the identity a run needs — the {@code jobId} and its {@code researchRunId} for
 * attribution, the {@code tripId} to research, and the {@code userId} that owns both — and a
 * {@link #reportProgress(int)} sink. The sink is a plain callback rather than a service reference so
 * the handler cannot reach into the platform's transitions: all it can do is advance a percentage.
 *
 * <p><strong>The handler runs outside any transaction</strong> (that is the whole point of the
 * platform — a 90s agent may not hold a database connection), so a handler that persists its result
 * does so in its own short {@code @TransactionalWrite} method, exactly as every other write does.
 */
public record ResearchJobContext(
        UUID jobId,
        UUID researchRunId,
        UUID tripId,
        UUID userId,
        IntConsumer progressSink) {

    /** Record coarse progress (0–100) for the poll UI. Advisory; best-effort; never ordering. */
    public void reportProgress(int progressPct) {
        progressSink.accept(progressPct);
    }
}
