package com.travelplanner.application.research;

import java.util.UUID;

/**
 * Raised when a research job has been persisted as {@code QUEUED} (tasks/23).
 *
 * <p>Published inside the start transaction and consumed by a {@code @TransactionalEventListener}
 * bound to {@code AFTER_COMMIT}, which is what makes "persist before dispatch" structural: if the
 * transaction rolls back, the listener never fires and no worker is handed a job that does not
 * exist. It also decouples the write path from the executor, so {@code ResearchJobService} depends
 * on the Spring event publisher rather than on the pool it would otherwise cycle with.
 */
public record ResearchJobQueuedEvent(UUID jobId) {
}
