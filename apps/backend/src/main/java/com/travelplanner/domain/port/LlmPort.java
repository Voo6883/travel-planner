package com.travelplanner.domain.port;

import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.ToolSpec;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * The project's LLM interface (PLAN §5.1, amended by ADR 007). Features depend on this and never on
 * a vendor SDK, so a provider swap costs one adapter package.
 *
 * <p>Naming: ADR 007 calls this {@code LlmPort}, PLAN §5.1 calls it {@code LlmClient}. The
 * {@code *Port} name is used because it is what ADR 007 (the amending document) says and what every
 * other port in {@code domain/port/} is called. The router in front of it keeps the name PLAN §5.4
 * gives it, {@code LlmClientRouter}.
 *
 * <h2>{@link #stream} returns {@code Flux<LlmEvent>}, not {@code Flux<String>}</h2>
 *
 * <p>ADR 007 supersedes PLAN §5.1's {@code Flux<String> stream(...)}. The reason is structural, not
 * stylistic: a token stream cannot carry a tool-use delta, a stop reason, usage, or a mid-stream
 * error, which makes the locked {@code trip_created} behaviour (PLAN §3.2) impossible to build on
 * it. Both providers stream events natively; the string form threw away the parts the product needs
 * and would have forced every consumer to parse them back out of text.
 *
 * <h2>Rules for implementations</h2>
 *
 * <ul>
 *   <li>Every failure surfaces as {@link com.travelplanner.domain.exception.AiProviderException} —
 *       never a vendor exception, never a raw {@code IOException}.</li>
 *   <li>A stream that fails after emitting terminates with {@link LlmEvent.StreamError} and then
 *       completes. Once the HTTP response is a {@code 200}, an error can no longer be a status.</li>
 *   <li>Cancelling the {@code Flux} must cancel the provider call. A subscriber that walked away is
 *       still being billed until the adapter stops.</li>
 *   <li>Adapters never emit {@link LlmEvent.DomainEvent}; the orchestrator emits it after a tool
 *       commits (ADR 007).</li>
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

    /** One turn of prose. */
    String complete(Prompt prompt, LlmOptions options);

    /**
     * Schema-bound output, bound to {@code type} and validated before it is returned (PLAN §6 item 3).
     *
     * @throws com.travelplanner.domain.exception.AiProviderException {@code ai_response_invalid} when
     *     the model cannot produce a conforming object. A typed failure, never a partially populated
     *     object — PLAN §4.1 requires "no confident result" to be a valid typed outcome rather than
     *     something invented to fill the fields.
     */
    <T> T completeStructured(Prompt prompt, Class<T> type, LlmOptions options);

    /**
     * One turn that may request tools.
     *
     * <p>Returns the model's <em>requests</em>. It does not execute anything: tool arguments are
     * unvalidated model output, and executing them here would put the schema check on the wrong side
     * of the boundary ({@code docs/AGENT-HARNESS.md} §4).
     */
    LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options);

    /** The streaming turn (ADR 007). Cold: nothing is sent until a subscriber arrives. */
    Flux<LlmEvent> stream(Prompt prompt, LlmOptions options);

    /** The streaming turn, with tools offered. */
    Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options);
}
