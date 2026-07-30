package com.travelplanner.ai.client;

import com.travelplanner.application.ai.LlmStreamPort;
import com.travelplanner.domain.port.LlmPort;

/**
 * Both halves of the LLM contract, which is what an <em>adapter</em> has to be.
 *
 * <p>The ports are segregated for callers — {@link LlmPort} blocks, {@link LlmStreamPort} streams,
 * and no caller in the system wants both. An adapter is the other side of that split and implements
 * the lot: Anthropic, OpenAI and the stub each serve completions and streams from the same
 * configuration, and so does {@link LlmClientRouter}, which has to be routable either way.
 *
 * <p>This type exists so the routing table has something to hold. {@code Map<String, LlmPort>} would
 * have no {@code stream}, and a cast in {@code StreamingCall} would put the one place that must never
 * be wrong — cancellation reaching the provider — behind an unchecked assumption.
 *
 * <p>It is also the single bean {@code AiConfig} publishes. One instance, injectable as either port,
 * so there is no ambiguity for Spring to resolve and no second router to fall out of step.
 */
public interface LlmProvider extends LlmPort, LlmStreamPort {
}
