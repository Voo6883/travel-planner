package com.travelplanner.application.research;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.config.ResearchExecutionConfig;
import com.travelplanner.config.ResearchProperties;
import com.travelplanner.domain.model.ResearchJob;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Drives one research job from {@code QUEUED} to a terminal state on a background thread (tasks/23).
 *
 * <p>It is the orchestration only; every durable transition is a call to {@link ResearchJobService}
 * (a separate bean, so the {@code @TransactionalWrite} boundaries are real). The sequence:
 *
 * <ol>
 *   <li>{@link ResearchJobService#beginRunning} — {@code QUEUED → RUNNING}. If it returns false the
 *       job was gone or already claimed, and the run stops with no side effect (a safe duplicate
 *       dispatch).</li>
 *   <li>Run the {@link ResearchJobHandler} on the timeout pool and {@link Future#get(long, TimeUnit)}
 *       it for the §14 budget. The handler executes with <em>no transaction held</em> — the whole
 *       reason the platform exists.</li>
 *   <li>Return → {@link ResearchJobService#markCompleted}. Timeout → interrupt and
 *       {@code research_timeout}. Throw → {@code research_failed}. Interrupted (shutdown) →
 *       {@code research_interrupted}.</li>
 * </ol>
 *
 * <p>No transaction spans the handler call (PLAN/AGENTS: no LLM/HTTP inside {@code @Transactional}),
 * so a 90s run never holds a pooled database connection.
 */
@Component
@RequiresDatabase
public class ResearchJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ResearchJobRunner.class);

    /** Written to {@code research_job.error_code} on the failure paths. */
    static final String TIMEOUT_ERROR = "research_timeout";
    static final String FAILED_ERROR = "research_failed";
    static final String INTERRUPTED_ERROR = "research_interrupted";

    private final ResearchJobService jobService;
    private final ResearchJobHandler handler;
    private final ExecutorService timeoutExecutor;
    private final long jobTimeoutMs;

    public ResearchJobRunner(ResearchJobService jobService, ResearchJobHandler handler,
            @Qualifier(ResearchExecutionConfig.TIMEOUT_EXECUTOR) ExecutorService timeoutExecutor,
            ResearchProperties properties) {
        this.jobService = jobService;
        this.handler = handler;
        this.timeoutExecutor = timeoutExecutor;
        this.jobTimeoutMs = properties.getJobTimeoutMs();
    }

    /** Runs the job to completion or failure. Never throws — every outcome is a recorded state. */
    public void run(UUID jobId) {
        ResearchJob job = jobService.beginRunning(jobId).orElse(null);
        if (job == null) {
            return;
        }
        ResearchJobContext context = contextFor(job);
        Future<?> work = timeoutExecutor.submit(() -> execute(context));
        try {
            work.get(jobTimeoutMs, TimeUnit.MILLISECONDS);
            jobService.markCompleted(jobId);
        } catch (TimeoutException overrun) {
            work.cancel(true);
            log.warn("research_job_timeout job={} budget_ms={}", jobId, jobTimeoutMs);
            jobService.failJob(jobId, TIMEOUT_ERROR);
        } catch (ExecutionException failure) {
            log.warn("research_job_handler_threw job={}", jobId, failure.getCause());
            jobService.failJob(jobId, FAILED_ERROR);
        } catch (InterruptedException shutdown) {
            Thread.currentThread().interrupt();
            work.cancel(true);
            jobService.failJob(jobId, INTERRUPTED_ERROR);
        }
    }

    private ResearchJobContext contextFor(ResearchJob job) {
        UUID jobId = job.id();
        return new ResearchJobContext(jobId, job.researchRunId(), job.tripId(), job.userId(),
                progressPct -> jobService.reportProgress(jobId, progressPct));
    }

    private void execute(ResearchJobContext context) {
        try {
            handler.execute(context);
        } catch (RuntimeException runtime) {
            throw runtime;
        } catch (Exception checked) {
            throw new HandlerFailure(checked);
        }
    }

    /** Wraps a checked handler failure so it surfaces through {@link Future#get()}. */
    private static final class HandlerFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        HandlerFailure(Throwable cause) {
            super(cause);
        }
    }
}
