package com.travelplanner.application.research;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.config.ResearchExecutionConfig;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The single-node {@link ResearchJobExecutor}: a bounded {@link TaskExecutor} that runs each job on
 * a background thread (tasks/23). Replaceable wholesale by a queue-backed executor behind a future
 * ADR — deliberately no Redis or broker here (the task's Do-not list).
 *
 * <p>Two entry points reach {@link #enqueue}:
 * <ul>
 *   <li>{@link #onJobQueued} — a {@code @TransactionalEventListener} bound to {@code AFTER_COMMIT},
 *       so a start dispatches only once its row is durably committed;</li>
 *   <li>the startup reconciler, which calls {@link #enqueue} directly to re-dispatch orphaned queued
 *       jobs (there is no transaction to hang an event on at boot).</li>
 * </ul>
 *
 * <p>The request's {@code X-Request-Id} is captured from the MDC at dispatch and restored on the
 * worker thread, so a background run's log lines trace back to the HTTP call that started it
 * (PLAN §4.0.2-J2).
 */
@Component
@RequiresDatabase
public class LocalResearchJobExecutor implements ResearchJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(LocalResearchJobExecutor.class);

    private final TaskExecutor taskExecutor;
    private final ResearchJobRunner runner;

    public LocalResearchJobExecutor(
            @Qualifier(ResearchExecutionConfig.WORKER_EXECUTOR) TaskExecutor taskExecutor,
            ResearchJobRunner runner) {
        this.taskExecutor = taskExecutor;
        this.runner = runner;
    }

    @Override
    public void enqueue(UUID jobId) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        taskExecutor.execute(() -> runWithContext(jobId, callerContext));
    }

    /** Dispatch only after the start transaction commits (persist before dispatch). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobQueued(ResearchJobQueuedEvent event) {
        enqueue(event.jobId());
    }

    private void runWithContext(UUID jobId, Map<String, String> callerContext) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (callerContext != null) {
            MDC.setContextMap(callerContext);
        }
        try {
            runner.run(jobId);
        } catch (RuntimeException unexpected) {
            // The runner already fails the job for handler errors; this only guards a defect in the
            // runner itself so a pool thread does not die silently.
            log.error("research_job_worker_error job={}", jobId, unexpected);
        } finally {
            if (previous != null) {
                MDC.setContextMap(previous);
            } else {
                MDC.clear();
            }
        }
    }
}
