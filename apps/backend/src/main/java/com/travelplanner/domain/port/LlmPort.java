package com.travelplanner.domain.port;

import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.ToolSpec;
import java.util.List;

/**
 * The project's blocking LLM interface (PLAN §5.1, amended by ADR 007). Features depend on this and
 * never on a vendor SDK, so a provider swap costs one adapter package.
 *
 * <p>Naming: ADR 007 calls this {@code LlmPort}, PLAN §5.1 calls it {@code LlmClient}. The
 * {@code *Port} name is used because it is what ADR 007 (the amending document) says and what every
 * other port in {@code domain/port/} is called. The router in front of it keeps the name PLAN §5.4
 * gives it, {@code LlmClientRouter}.
 *
 * <h2>Streaming is not here</h2>
 *
 * <p>The streaming turn lives on {@code application.ai.LlmStreamPort}, not on this interface, and
 * that is the whole reason this file has no third-party import. ADR 007 types the stream as
 * {@code Flux<LlmEvent>} — Reactor, a framework — and a framework type on a domain port makes the
 * domain unconstructible without it, which is the one property this layer exists to have. The events
 * themselves stay in {@code domain/ai} as plain value types; only the publisher moved out.
 *
 * <p>The split is not merely bookkeeping. {@code ChatTurnService} streams and never completes;
 * {@code StructuredOutputRunner} completes and never streams. Neither had a use for the other's
 * methods, so segregating them costs nothing and each caller now declares what it actually needs.
 *
 * <h2>Rules for implementations</h2>
 *
 * <ul>
 *   <li>Every failure surfaces as {@link com.travelplanner.domain.exception.AiProviderException} —
 *       never a vendor exception, never a raw {@code IOException}.</li>
 * </ul>
 *
 * <h2>Rules for callers</h2>
 *
 * <ul>
 *   <li>Never inject this into a controller (PLAN §4.0.1) — services only.</li>
 *   <li>Never call it inside {@code @Transactional} (AGENTS.md): the call takes seconds and would
 *       hold a pooled connection for all of them.</li>
 * </ul>
 */
public interface LlmPort {

    /** Which provider answers — {@code "anthropic"}, {@code "openai"}, {@code "stub"}. For logs. */
    String providerName();

    /**
     * The model this implementation calls when {@link LlmOptions#model()} is unset.
     *
     * <p>Required, not optional, because it is a billing input. {@code ai_call_log.model} is what
     * {@code AiCostEstimator} looks a price up by, and a caller almost never overrides the model —
     * so an implementation that could not name its own default would leave the column blank on
     * nearly every row, and every cost in the table would be zero while looking populated. Reading
     * it back off the response is not an alternative: a failed call has no response and still costs
     * a retry budget.
     */
    String modelName();

    /**
     * One turn of prose.
     *
     * <p>Structured output is deliberately absent from this port. It is composed one layer up by
     * {@code ai/structured/StructuredOutputRunner} over this method, so the schema instruction, the
     * JSON extraction, the bounded repair attempt, and the typed failure exist once for every
     * provider — and so every attempt is an ordinary routed call that lands in {@code ai_call_log}
     * with its own tokens. A {@code completeStructured} on the port would return a bare {@code T},
     * which is a shape with nowhere to put usage.
     */
    String complete(Prompt prompt, LlmOptions options);

    /**
     * One turn that may request tools.
     *
     * <p>Returns the model's <em>requests</em>. It does not execute anything: tool arguments are
     * unvalidated model output, and executing them here would put the schema check on the wrong side
     * of the boundary ({@code docs/AGENT-HARNESS.md} §4).
     */
    LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options);

}
