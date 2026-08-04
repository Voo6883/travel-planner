package com.travelplanner.application.research;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * The default {@link ResearchJobHandler} until task 25 lands the real research agent.
 *
 * <p>It does no research: it reports full progress and returns, so the platform completes the job
 * and moves the trip to {@code RESEARCH_READY}. That makes the whole durable-job machinery testable
 * and demonstrable end to end — start, run, poll, complete — with no agent and no recommendations,
 * which is exactly the boundary tasks/23 owns and tasks/25 fills.
 *
 * <p>{@code @ConditionalOnMissingBean}: the moment task 25 registers a real handler, this stands
 * aside with no edit here.
 */
@Component
@ConditionalOnMissingBean(ResearchJobHandler.class)
public class NoOpResearchJobHandler implements ResearchJobHandler {

    private static final Logger log = LoggerFactory.getLogger(NoOpResearchJobHandler.class);

    @Override
    public void execute(ResearchJobContext context) {
        log.info("research_job_noop_handler job={} trip={} — completing without recommendations",
                context.jobId(), context.tripId());
        context.reportProgress(100);
    }
}
