package com.travelplanner.ai.replay;

import com.travelplanner.ai.client.LlmProvider;
import com.travelplanner.ai.observability.PromptHasher;
import com.travelplanner.ai.replay.RecordedExchange.RecordedCompletion;
import com.travelplanner.ai.replay.RecordedExchange.RecordedToolCall;
import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.LlmToolCall;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * Serves recorded provider output instead of calling a provider (review §6.I).
 *
 * <h2>What this is for, and what the stub already covers</h2>
 *
 * <p>{@code StubLlmAdapter} answers everything with {@code [stub] no model configured. Received: …}.
 * That is the right default — it is deterministic, needs no credentials, and is visibly not a model
 * answer, which is what keeps a fabricated travel fact from ever looking real. It is also, by
 * construction, useless as a <em>protocol</em> fixture: no test that runs against it has ever seen an
 * Anthropic tool-call delta sequence, an OpenAI response with prose wrapped around its JSON, or a
 * stream whose usage frame arrives in an unexpected position.
 *
 * <p>This adapter replays real recorded output offline. The review's phrasing is exact: cost, latency
 * variance and flakiness go away, and "真实协议兼容性" — real protocol compatibility — stays. Live
 * providers are then only needed for an explicit eval job, not for the everyday suite.
 *
 * <h2>An unmatched prompt is a failure, never a fallback</h2>
 *
 * <p>The obvious convenience is to fall through to the stub when no recording matches. It is refused
 * here, and that is the single most important decision in this class. A silent fallback means a test
 * whose prompt changed keeps passing — against stub output that asserts nothing about the provider —
 * and the fixture it was supposed to exercise is quietly dead. The suite stays green and stops
 * testing anything, which is the worst available outcome.
 *
 * <p>So a miss throws {@code ai_unavailable} with the prompt hash and the recording command in the
 * message. Loud, actionable, and impossible to mistake for a pass.
 *
 * <h2>Never in production</h2>
 *
 * <p>{@code AiConfigValidator} refuses {@code replay} under the {@code prod} profile, alongside
 * {@code stub}. The reason is stronger here than for the stub: stub output announces itself, while
 * replayed output is indistinguishable from a live answer — it <em>was</em> one, for a different
 * traveller, at some point in the past. A production deployment serving recorded answers would look
 * entirely healthy.
 */
public final class ReplayLlmAdapter implements LlmProvider {

    /** The provider name this adapter reports, and the value {@code travelplanner.ai} accepts. */
    public static final String PROVIDER_NAME = "replay";

    private final RecordedExchangeStore store;

    public ReplayLlmAdapter(RecordedExchangeStore store) {
        this.store = store;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    /**
     * The model of whichever recording is being served is the honest answer, and it is not knowable
     * here — this method has no prompt. So the adapter reports itself, and per-call the router already
     * prefers {@link LlmOptions#model()}; a recorded exchange carries its original model for anyone
     * reading the fixture.
     *
     * <p>Deliberately not a real model name: that would put a live provider's price into
     * {@code ai_call_log} for a call that cost nothing, and a cost dashboard containing fictional money
     * is worse than one containing none.
     */
    @Override
    public String modelName() {
        return PROVIDER_NAME;
    }

    @Override
    public String complete(Prompt prompt, LlmOptions options) {
        return completeWithTools(prompt, List.of(), options).text();
    }

    @Override
    public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        RecordedExchange exchange = require(prompt, RecordedExchange::canServeCompletion, "a completion");
        RecordedCompletion recorded = exchange.completion();
        return new LlmCompletion(
                recorded.text(),
                recorded.toolCalls().stream().map(ReplayLlmAdapter::toDomain).toList(),
                new LlmEvent.Usage(recorded.usage().inputTokens(), recorded.usage().outputTokens(),
                        recorded.usage().cachedTokens()),
                recorded.stopReason());
    }

    @Override
    public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
        return stream(prompt, List.of(), options);
    }

    /**
     * The recorded events, in arrival order.
     *
     * <p>{@code Flux.defer} so the lookup happens at subscribe time rather than at assembly time. A
     * cold Flux may be built long before anyone subscribes, and a missing-fixture failure raised at
     * assembly would surface from whichever line constructed the publisher instead of from the call.
     * It also matches the live adapters, which is the point of a replay adapter: a consumer must not be
     * able to tell which one it has.
     *
     * <p>No artificial delay between events. Real inter-token timing is exactly the source of variance
     * this exists to remove, and a test that needs slow arrival can interpose its own operator.
     */
    @Override
    public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
        return Flux.defer(() -> {
            RecordedExchange exchange = require(prompt, RecordedExchange::canServeStream, "a stream");
            return Flux.fromIterable(exchange.events().stream().map(RecordedEventMapper::toDomain).toList());
        });
    }

    private static LlmToolCall toDomain(RecordedToolCall recorded) {
        return new LlmToolCall(recorded.id(), recorded.name(), recorded.argumentsJson());
    }

    /**
     * The recording for this prompt, or a failure that says what to do about it.
     *
     * <p>The message carries the prompt hash and the exact command to record it, because the two things
     * somebody needs at that moment are "which fixture is missing" and "how do I make it". Listing the
     * hashes that <em>are</em> present is what turns "no fixture" into "the prompt changed" — the usual
     * cause, and invisible without it.
     */
    private RecordedExchange require(Prompt prompt, java.util.function.Predicate<RecordedExchange> usable,
            String what) {
        String promptHash = PromptHasher.hash(prompt);
        RecordedExchange exchange = store.find(promptHash).orElse(null);

        if (exchange == null) {
            throw AiProviderException.unavailable(PROVIDER_NAME + ": no recorded exchange for prompt hash "
                    + promptHash + ". " + store.size() + " recording(s) are loaded, so this prompt has "
                    + "probably changed since it was captured. Record it with "
                    + "AI_PROVIDER_DEFAULT=<real provider> and travelplanner.ai.replay.record=true, then "
                    + "commit the fixture. There is deliberately no fallback to the stub: a test that "
                    + "silently passed on stub output would stop exercising the provider protocol.");
        }
        if (!usable.test(exchange)) {
            throw AiProviderException.unavailable(PROVIDER_NAME + ": the recording for " + promptHash
                    + " has no " + what + " half. Re-record it through the call shape you are testing — "
                    + "a completion recording cannot serve a stream, and replaying one as the other "
                    + "would fabricate an event sequence no provider produced.");
        }
        return exchange;
    }
}
