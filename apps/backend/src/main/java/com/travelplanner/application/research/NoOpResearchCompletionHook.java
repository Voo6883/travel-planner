package com.travelplanner.application.research;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * The default {@link ResearchCompletionHook} until task 25 chooses to use it. Does nothing, so the
 * platform simply moves the trip to {@code RESEARCH_READY} on completion.
 *
 * <p>{@code @ConditionalOnMissingBean}: task 25 registers its own to persist recommendations in the
 * completion transaction, and this stands aside with no edit here.
 */
@Component
@ConditionalOnMissingBean(ResearchCompletionHook.class)
public class NoOpResearchCompletionHook implements ResearchCompletionHook {

    @Override
    public void onResearchCompleted(UUID jobId, UUID tripId) {
        // No recommendations to persist yet; task 25 replaces this bean.
    }
}
