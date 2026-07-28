package com.travelplanner.ai.client;

import com.travelplanner.ai.observability.AiCallRecorder;
import com.travelplanner.ai.resilience.AiRetryPolicy;
import com.travelplanner.ai.resilience.CircuitBreakerGate;

/**
 * The cross-cutting collaborators every routed call passes through: retry, breaker, and the
 * {@code ai_call_log} recorder.
 *
 * <p>Bundled so {@link LlmClientRouter}'s constructor keeps two parameters. Grouping them is not only
 * arithmetic — they are the three things that must be applied to <em>every</em> call, so having one
 * type for "the instrumentation" makes it obvious when a new code path skipped it.
 */
public record RouterSupport(AiRetryPolicy retryPolicy, CircuitBreakerGate breaker,
        AiCallRecorder recorder) {
}
