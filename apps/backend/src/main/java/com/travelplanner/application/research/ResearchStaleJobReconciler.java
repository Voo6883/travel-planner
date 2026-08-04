package com.travelplanner.application.research;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.ResearchJobStatus;
import com.travelplanner.domain.model.ResearchJob;
import com.travelplanner.domain.port.ResearchJobRepositoryPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Restart recovery for research jobs (tasks/23: "restart recovery policy" and "stale-running-job
 * reconciliation").
 *
 * <p>Runs once, after the context is refreshed and Flyway has migrated — the same
 * {@link ApplicationRunner} point {@code DevAdminSeeder} uses. On a single node, a job that is
 * {@code RUNNING} in the database at boot cannot belong to this just-started process: its worker
 * thread died with the previous one. Rather than leave the trip stranded in {@code RESEARCH_RUNNING}
 * behind a dead job, every such job is failed with {@code research_timeout} and its trip recovered
 * to {@code BRIEF_COMPLETE}, so the user can re-run (UC-C2-07). This is a strict superset of the
 * task's "older than the timeout" rule — at boot they are all stale — and is the honest reading for
 * a single-node deployment; a multi-node one would swap this for the age-filtered {@code @Scheduled}
 * sweep the task offers as the alternative.
 *
 * <p>{@code QUEUED} jobs are the other orphan: committed but never picked up (the process died
 * between commit and dispatch, or mid-dispatch). They are simply re-enqueued, which lets them run
 * exactly as a fresh start would — no data is lost and no trip is stuck.
 */
@Component
@RequiresDatabase
public class ResearchStaleJobReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ResearchStaleJobReconciler.class);

    private final ResearchJobRepositoryPort jobs;
    private final ResearchJobService jobService;
    private final ResearchJobExecutor executor;

    public ResearchStaleJobReconciler(ResearchJobRepositoryPort jobs,
            ResearchJobService jobService, ResearchJobExecutor executor) {
        this.jobs = jobs;
        this.jobService = jobService;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        reconcile();
    }

    /** Fail orphaned running jobs and re-dispatch orphaned queued ones. Idempotent to re-run. */
    public void reconcile() {
        List<ResearchJob> running = jobs.findByStatus(ResearchJobStatus.RUNNING);
        for (ResearchJob job : running) {
            log.warn("research_job_reconcile_orphaned_running job={} trip={}", job.id(), job.tripId());
            jobService.failJob(job.id(), ResearchJobRunner.TIMEOUT_ERROR);
        }

        List<ResearchJob> queued = jobs.findByStatus(ResearchJobStatus.QUEUED);
        for (ResearchJob job : queued) {
            log.info("research_job_reconcile_redispatch_queued job={} trip={}", job.id(), job.tripId());
            executor.enqueue(job.id());
        }

        if (!running.isEmpty() || !queued.isEmpty()) {
            log.info("research_job_reconcile_done failed_running={} redispatched_queued={}",
                    running.size(), queued.size());
        }
    }
}
