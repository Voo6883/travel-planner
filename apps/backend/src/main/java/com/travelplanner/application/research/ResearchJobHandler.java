package com.travelplanner.application.research;

/**
 * The one seam task 25 plugs the research agent into (tasks/23 Definition of Done: "Later research
 * logic has a clear execution/persistence callback boundary").
 *
 * <p>The platform owns everything durable: it moves the job {@code QUEUED → RUNNING}, enforces the
 * §14 timeout, marks the job {@code COMPLETED}/{@code FAILED}, and drives {@code trip.status}. The
 * handler owns the <em>work</em>: given a {@link ResearchJobContext}, it runs the agent (LLM/HTTP
 * are permitted here because this executes on a background worker, never inside a transaction),
 * reports progress, and persists its recommendations in its own short transactional method.
 *
 * <p>Contract:
 * <ul>
 *   <li><b>Return normally</b> → the platform marks the job {@code COMPLETED} and transitions the
 *       trip {@code RESEARCH_RUNNING → RESEARCH_READY} (invoking {@link ResearchCompletionHook}).</li>
 *   <li><b>Throw</b> → the platform marks the job {@code FAILED} with an error code and recovers the
 *       trip to {@code BRIEF_COMPLETE} so the user can re-run (UC-C2-07).</li>
 *   <li><b>Overrun the timeout</b> → the platform interrupts the worker thread, so a long call must
 *       honour interruption, and marks the job {@code research_timeout}.</li>
 * </ul>
 */
public interface ResearchJobHandler {

    /**
     * Runs one research job to a result, or throws to fail it.
     *
     * @throws Exception any failure; the platform maps it to a job {@code error_code}
     */
    void execute(ResearchJobContext context) throws Exception;
}
