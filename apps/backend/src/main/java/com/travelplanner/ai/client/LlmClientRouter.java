package com.travelplanner.ai.client;

import com.travelplanner.ai.observability.AiCallContext;
import com.travelplanner.ai.observability.AiCallRecorder;
import com.travelplanner.ai.observability.PromptHasher;
import com.travelplanner.ai.resilience.AiRetryPolicy;
import com.travelplanner.ai.resilience.CircuitBreakerGate;
import com.travelplanner.domain.ai.AiOperation;
import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.port.LlmPort;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * The bean every feature injects (PLAN §5.4: "{@code LlmClientRouter implements LlmClient} picks the
 * active provider from config; callers still just inject {@code LlmClient}").
 *
 * <p>It is the single point where a call acquires a provider, a retry budget, a breaker check, and an
 * {@code ai_call_log} row. Putting those in the adapters instead would mean writing them three times
 * — Anthropic, OpenAI, stub — and the stub's version would be the one that quietly diverged, so the
 * test suite would stop exercising the behaviour it was meant to cover.
 *
 * <h2>Routing</h2>
 *
 * <p>{@code LlmOptions.feature()} selects a provider through {@code travelplanner.ai.routing},
 * falling back to the default. An unknown <em>feature</em> key falls back silently, because a feature
 * that never configured routing is the normal case. An unknown <em>provider</em> name does not: it is
 * rejected at startup by {@code AiConfigValidator}, since a typo there would run a feature on the
 * wrong model indefinitely with nothing to notice.
 *
 * <h2>Why streaming is handled separately</h2>
 *
 * <p>Retry wraps the blocking methods only. A stream that fails after emitting text cannot be
 * retried: the consumer has already rendered the first attempt's tokens, and appending a second
 * attempt's answer to them produces a message no model ever wrote. ADR 007's {@code StreamError}
 * frame is the substitute — the failure is made explicit and the decision handed to the caller.
 */
public final class LlmClientRouter implements LlmPort {

    private final Map<String, LlmPort> providers;
    private final Map<String, String> routing;
    private final String defaultProvider;
    private final AiRetryPolicy retryPolicy;
    private final CircuitBreakerGate breaker;
    private final AiCallRecorder recorder;

    public LlmClientRouter(RoutingTable routingTable, RouterSupport support) {
        this.providers = routingTable.providers();
        this.routing = routingTable.routing();
        this.defaultProvider = routingTable.defaultProvider();
        this.retryPolicy = support.retryPolicy();
        this.breaker = support.breaker();
        this.recorder = support.recorder();
    }

    /** Which provider a caller with no feature preference reaches. */
    @Override
    public String providerName() {
        return defaultProvider;
    }

    /** The provider configured for {@code feature}, or the default. Exposed for diagnostics. */
    public String providerFor(String feature) {
        String configured = routing.get(feature);
        return configured == null || !providers.containsKey(configured) ? defaultProvider : configured;
    }

    @Override
    public String complete(Prompt prompt, LlmOptions options) {
        return guarded(new CallSpec(prompt, options, AiOperation.COMPLETE),
                provider -> provider.complete(prompt, options));
    }

    @Override
    public <T> T completeStructured(Prompt prompt, Class<T> type, LlmOptions options) {
        return guarded(new CallSpec(prompt, options, AiOperation.COMPLETE_STRUCTURED),
                provider -> provider.completeStructured(prompt, type, options));
    }

    @Override
    public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        return guarded(new CallSpec(prompt, options, AiOperation.COMPLETE_WITH_TOOLS),
                provider -> provider.completeWithTools(prompt, tools, options));
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
        return stream(prompt, List.of(), options);
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        return new StreamingCall(this, recorder).execute(new CallSpec(prompt, options,
                AiOperation.STREAM), tools);
    }

    /** One blocking call's inputs, bundled so the helpers stay within ≤3 parameters (PLAN §4.0.4). */
    record CallSpec(Prompt prompt, LlmOptions options, AiOperation operation) {
    }

    /**
     * Runs a blocking call with the breaker, the retry budget, and an {@code ai_call_log} row.
     *
     * <p>Timing brackets the whole thing including retries, because "how long did the user wait" is
     * the number that matters, not "how long did the final attempt take".
     */
    private <T> T guarded(CallSpec spec, Function<LlmPort, T> call) {
        LlmPort provider = resolve(spec.options());
        AiCallContext context = contextFor(spec);
        requireClosedBreaker(provider.providerName());
        long startedAt = System.nanoTime();
        try {
            T result = retryPolicy.execute(() -> call.apply(provider));
            breaker.recordSuccess(provider.providerName());
            recorder.recordSuccess(context, usageOf(result), elapsedMs(startedAt));
            return result;
        } catch (AiProviderException failure) {
            breaker.recordFailure(provider.providerName());
            recorder.recordFailure(context, failure.code(), elapsedMs(startedAt));
            throw failure;
        }
    }

    /** Package-private so {@link StreamingCall} composes the same routing and instrumentation. */
    LlmPort resolve(LlmOptions options) {
        String name = providerFor(options == null ? LlmOptions.DEFAULT_FEATURE : options.feature());
        LlmPort provider = providers.get(name);
        if (provider == null) {
            throw AiProviderException.unavailable("no adapter is registered for provider " + name);
        }
        return provider;
    }

    AiCallContext contextFor(CallSpec spec) {
        LlmOptions options = spec.options();
        String feature = options == null ? LlmOptions.DEFAULT_FEATURE : options.feature();
        return new AiCallContext(feature, spec.operation(), resolve(options).providerName(),
                modelOf(options), null, PromptHasher.hash(spec.prompt()));
    }

    void requireClosedBreaker(String provider) {
        if (!breaker.allowRequest(provider)) {
            throw AiProviderException.unavailable(provider + ": circuit breaker is open");
        }
    }

    CircuitBreakerGate breaker() {
        return breaker;
    }

    static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private static LlmEvent.Usage usageOf(Object result) {
        return result instanceof LlmCompletion completion ? completion.usage() : LlmEvent.Usage.none();
    }

    private static String modelOf(LlmOptions options) {
        // Blank when the caller did not override: the adapter's configured model is the truth, and
        // guessing it here would put a wrong value into the cost estimate.
        return options == null || options.model() == null ? "" : options.model();
    }
}
