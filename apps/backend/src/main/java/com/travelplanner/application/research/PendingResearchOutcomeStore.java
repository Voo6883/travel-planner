package com.travelplanner.application.research;

import com.travelplanner.domain.model.ResearchRunResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Hands a finished research outcome from the handler (no TX) to the completion hook (inside the
 * COMPLETED / RESEARCH_READY transaction).
 *
 * <p>Keyed by job id. The handler {@link #put(UUID, ResearchRunResult) put}s before returning; the
 * hook {@link #take(UUID) take}s and persists. A missing entry is a bug — the hook fails the
 * transaction so RESEARCH_READY never appears without a durable marker.
 */
@Component
public class PendingResearchOutcomeStore {

    private final ConcurrentMap<UUID, ResearchRunResult> pending = new ConcurrentHashMap<>();

    public void put(UUID jobId, ResearchRunResult result) {
        pending.put(jobId, result);
    }

    public Optional<ResearchRunResult> take(UUID jobId) {
        return Optional.ofNullable(pending.remove(jobId));
    }
}
