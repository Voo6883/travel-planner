package com.travelplanner.application.research;

import java.util.UUID;

/**
 * The atomic completion seam (tasks/23 Definition of Done: "Successful completion transition hook
 * for later recommendation persistence and {@code RESEARCH_READY} update").
 *
 * <p>Invoked <em>inside</em> the same transaction that marks the job {@code COMPLETED} and moves the
 * trip to {@code RESEARCH_READY}. Task 25 may implement this to persist its ranked recommendations
 * in lock-step with the status move, so a client that sees {@code RESEARCH_READY} is guaranteed the
 * recommendations exist — or, equally, may persist inside its {@link ResearchJobHandler} and leave
 * this a no-op. Both are valid; the seam exists so the atomic option does not require re-plumbing
 * the platform.
 *
 * <p>Because it runs in a transaction, an implementation must do <em>persistence only</em> here — no
 * LLM, no HTTP. The expensive work already happened in the handler.
 */
public interface ResearchCompletionHook {

    /**
     * Called after the job is marked complete and before the trip becomes {@code RESEARCH_READY},
     * within one transaction.
     *
     * @param jobId the completed job
     * @param tripId the trip it researched
     */
    void onResearchCompleted(UUID jobId, UUID tripId);
}
