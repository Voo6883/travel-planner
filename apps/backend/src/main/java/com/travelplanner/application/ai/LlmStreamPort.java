package com.travelplanner.application.ai;

import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.ToolSpec;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * The streaming LLM turn (ADR 007).
 *
 * <h2>Why this is not in {@code domain/port} with every other port</h2>
 *
 * <p>It returns {@code Flux<LlmEvent>}, and {@code Flux} is Reactor — a framework. It used to sit on
 * {@link com.travelplanner.domain.port.LlmPort}, which made the domain layer's one guaranteed
 * property false: that its types are constructible and assertable with no framework on the classpath.
 * {@code LayerRulesTest.domainIsFrameworkFree} had to omit {@code reactor..} from its forbidden list
 * to pass, and an allow-list entry is a rule that has been talked out of applying (recorded as F-23).
 *
 * <p>Two other fixes were available and are worse. Moving the whole port here would put one port in
 * {@code application/} and twenty in {@code domain/port/}, trading a real inconsistency for a
 * cosmetic one. Retyping the stream as {@code java.util.concurrent.Flow.Publisher} would keep it in
 * the domain at the cost of a Reactor↔Flow conversion at every boundary — and cancellation
 * propagation is exactly what ADR 007 depends on ("a subscriber that walked away is still being
 * billed until the adapter stops"), so it is the last thing to route through an adapter nobody tests.
 *
 * <p>Segregating the two methods costs nothing, because no caller wanted both:
 * {@code ChatTurnService} streams and never completes, {@code StructuredOutputRunner} completes and
 * never streams. The value types stay in {@code domain/ai}; only the publisher lives out here.
 *
 * <h2>{@code Flux<LlmEvent>}, not {@code Flux<String>}</h2>
 *
 * <p>ADR 007 supersedes PLAN §5.1's {@code Flux<String> stream(...)}. The reason is structural, not
 * stylistic: a token stream cannot carry a tool-use delta, a stop reason, usage, or a mid-stream
 * error, which makes the locked {@code trip_created} behaviour (PLAN §3.2) impossible to build on it.
 * Both providers stream events natively; the string form threw away the parts the product needs and
 * would have forced every consumer to parse them back out of text.
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
 */
public interface LlmStreamPort {

    /** The streaming turn. Cold: nothing is sent until a subscriber arrives. */
    Flux<LlmEvent> stream(Prompt prompt, LlmOptions options);

    /** The streaming turn, with tools offered. */
    Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options);
}
