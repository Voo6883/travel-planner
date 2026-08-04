package com.travelplanner.application.research;

import java.util.UUID;

/**
 * The dispatch seam: hands a persisted job to a background worker (tasks/23).
 *
 * <p>An interface, not a concrete pool, because the single-node {@link LocalResearchJobExecutor} is
 * explicitly "suitable to replace later" — a queue-backed executor (Redis, SQS, …) behind a future
 * ADR would implement this same method and nothing calling it would change. Callers dispatch a job
 * <em>after</em> its row is committed; the id is all a worker needs to load and run it.
 */
public interface ResearchJobExecutor {

    /** Schedule the already-persisted job for execution. Returns immediately. */
    void enqueue(UUID jobId);
}
