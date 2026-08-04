package com.travelplanner.application.research;

import java.util.UUID;

/**
 * The default {@link ResearchCompletionHook} until task 25 chooses to use it. Does nothing, so the
 * platform simply moves the trip to {@code RESEARCH_READY} on completion.
 *
 * <p>Registered as a {@code @Bean} with {@code @ConditionalOnMissingBean} in
 * {@code ResearchExecutionConfig} — component-scanned {@code @ConditionalOnMissingBean} is unreliable
 * for the same type being registered (Spring Boot processes those conditions for auto-config, not
 * for ordinary component scanning), which is how the integration suite lost every context that
 * needed {@code ResearchJobService}.
 */
public class NoOpResearchCompletionHook implements ResearchCompletionHook {

    @Override
    public void onResearchCompleted(UUID jobId, UUID tripId) {
        // No recommendations to persist yet; task 25 replaces this bean.
    }
}
