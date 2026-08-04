package com.travelplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Research-job platform configuration (tasks/23, PLAN §14).
 *
 * <p>The one value that matters is {@link #getJobTimeoutMs()}: the wall-clock budget a single run
 * gets before the platform interrupts it and marks the job {@code research_timeout}. It defaults to
 * 90 000 ms — PLAN §14's p95 agent budget — so a fresh checkout matches the plan with no
 * configuration. Milliseconds rather than a {@code Duration} to match the {@code chat.heartbeat}
 * precedent, which chose a primitive so the value is dependable without Boot's conversion service.
 *
 * <p>The pool sizes bound a single node's concurrency; a queue-backed executor (a future ADR) would
 * replace {@link com.travelplanner.application.research.LocalResearchJobExecutor} wholesale rather
 * than tune these.
 */
@ConfigurationProperties(prefix = "travelplanner.research")
public class ResearchProperties {

    /** PLAN §14 p95 agent budget. */
    public static final long DEFAULT_JOB_TIMEOUT_MS = 90_000L;

    private long jobTimeoutMs = DEFAULT_JOB_TIMEOUT_MS;
    private int workerPoolSize = 2;
    private int workerQueueCapacity = 100;

    /** Bound from {@code travelplanner.research.job-timeout-ms}. */
    public long getJobTimeoutMs() {
        return jobTimeoutMs;
    }

    public void setJobTimeoutMs(long jobTimeoutMs) {
        this.jobTimeoutMs = jobTimeoutMs;
    }

    /** How many runs may execute at once on this node. */
    public int getWorkerPoolSize() {
        return workerPoolSize;
    }

    public void setWorkerPoolSize(int workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
    }

    /** How many runs may wait for a worker before a start is rejected by the pool. */
    public int getWorkerQueueCapacity() {
        return workerQueueCapacity;
    }

    public void setWorkerQueueCapacity(int workerQueueCapacity) {
        this.workerQueueCapacity = workerQueueCapacity;
    }
}
