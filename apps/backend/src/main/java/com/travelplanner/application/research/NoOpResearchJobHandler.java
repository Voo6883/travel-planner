package com.travelplanner.application.research;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default {@link ResearchJobHandler} until task 25 lands the real research agent.
 *
 * <p>It does no research: it reports full progress and returns, so the platform completes the job
 * and moves the trip to {@code RESEARCH_READY}. That makes the whole durable-job machinery testable
 * and demonstrable end to end — start, run, poll, complete — with no agent and no recommendations.
 *
 * <p>Registered as a {@code @Bean} with {@code @ConditionalOnMissingBean} in
 * {@code ResearchExecutionConfig} (same reason as {@link NoOpResearchCompletionHook}).
 */
public class NoOpResearchJobHandler implements ResearchJobHandler {

    private static final Logger log = LoggerFactory.getLogger(NoOpResearchJobHandler.class);

    @Override
    public void execute(ResearchJobContext context) {
        log.info("research_job_noop_handler job={} trip={} — completing without recommendations",
                context.jobId(), context.tripId());
        context.reportProgress(100);
    }
}
