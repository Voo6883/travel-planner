package com.travelplanner.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.ai.observability.AiCallRecorder;
import com.travelplanner.ai.resilience.AiRetryPolicy;
import com.travelplanner.ai.resilience.CircuitBreakerGate;
import com.travelplanner.ai.resilience.CountingCircuitBreakerGate;
import com.travelplanner.config.AiProperties;
import com.travelplanner.domain.ai.AiCallRecord;
import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.port.AiCallLogPort;
import com.travelplanner.domain.port.LlmPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** Routing, retry, breaker, and {@code ai_call_log} — the four things every call passes through. */
class LlmClientRouterTest {

    private final RecordingCallLog callLog = new RecordingCallLog();
    private final FakeLlm anthropic = new FakeLlm("anthropic");
    private final FakeLlm openai = new FakeLlm("openai");

    // ---------------------------------------------------------------------------------------
    // Routing (PLAN §5.4)
    // ---------------------------------------------------------------------------------------

    @Test
    void sendsAFeatureWithNoRoutingEntryToTheDefaultProvider() {
        LlmClientRouter router = router(Map.of("research", "openai"), "anthropic");

        router.complete(prompt(), LlmOptions.forFeature("nl-search"));

        assertThat(anthropic.completeCalls.get()).isEqualTo(1);
        assertThat(openai.completeCalls.get()).isZero();
    }

    @Test
    void sendsARoutedFeatureToItsConfiguredProvider() {
        LlmClientRouter router = router(Map.of("research", "openai"), "anthropic");

        router.complete(prompt(), LlmOptions.forFeature("research"));

        assertThat(openai.completeCalls.get()).isEqualTo(1);
    }

    /**
     * A routing entry naming an unregistered provider falls back rather than failing at call time.
     * The typo is caught at startup by {@code AiConfigValidator}; failing here as well would turn one
     * clear boot failure into a runtime failure for one feature only.
     */
    @Test
    void fallsBackWhenARoutingEntryNamesAProviderWithNoAdapter() {
        LlmClientRouter router = router(Map.of("research", "gemini"), "anthropic");

        assertThat(router.providerFor("research")).isEqualTo("anthropic");
    }

    // ---------------------------------------------------------------------------------------
    // Retry (task 14 Scope: "bounded retries")
    // ---------------------------------------------------------------------------------------

    @Test
    void retriesARetryableFailureUpToTheConfiguredBudget() {
        anthropic.failuresBeforeSuccess = 2;
        anthropic.failure = AiProviderException.unavailable("anthropic");

        String answer = router(Map.of(), "anthropic").complete(prompt(), LlmOptions.defaults());

        assertThat(answer).isEqualTo("ok");
        assertThat(anthropic.completeCalls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryATimeoutBecauseTheCallAlreadySpentItsWholeBudget() {
        anthropic.failuresBeforeSuccess = 1;
        anthropic.failure = AiProviderException.timeout("anthropic");

        assertThatThrownBy(() -> router(Map.of(), "anthropic").complete(prompt(), LlmOptions.defaults()))
                .isInstanceOf(AiProviderException.class)
                .extracting("code").isEqualTo("ai_timeout");
        assertThat(anthropic.completeCalls.get()).isEqualTo(1);
    }

    @Test
    void givesUpOnceTheAttemptBudgetIsExhausted() {
        anthropic.failuresBeforeSuccess = 99;
        anthropic.failure = AiProviderException.rateLimited("anthropic");

        assertThatThrownBy(() -> router(Map.of(), "anthropic").complete(prompt(), LlmOptions.defaults()))
                .isInstanceOf(AiProviderException.class);
        assertThat(anthropic.completeCalls.get()).isEqualTo(3);
    }

    // ---------------------------------------------------------------------------------------
    // Circuit breaker
    // ---------------------------------------------------------------------------------------

    @Test
    void stopsCallingAProviderOnceItsBreakerOpens() {
        anthropic.failuresBeforeSuccess = 99;
        anthropic.failure = AiProviderException.unavailable("anthropic");
        LlmClientRouter router = router(Map.of(), "anthropic");

        // A "failure" is one call that exhausted its retry budget, not one attempt — the retried
        // attempts are the platform working as designed, not evidence the provider is down.
        for (int call = 0; call < 5; call++) {
            assertThatThrownBy(() -> router.complete(prompt(), LlmOptions.defaults()))
                    .isInstanceOf(AiProviderException.class);
        }
        int callsBefore = anthropic.completeCalls.get();

        assertThatThrownBy(() -> router.complete(prompt(), LlmOptions.defaults()))
                .hasMessageContaining("circuit breaker is open");
        assertThat(anthropic.completeCalls.get()).isEqualTo(callsBefore);
    }

    // ---------------------------------------------------------------------------------------
    // ai_call_log (backlog S2-5)
    // ---------------------------------------------------------------------------------------

    @Test
    void recordsTokensProviderAndOutcomeForASuccessfulCall() {
        router(Map.of(), "anthropic").completeWithTools(prompt(), List.<ToolSpec>of(),
                LlmOptions.defaults());

        assertThat(callLog.records).singleElement().satisfies(record -> {
            assertThat(record.provider()).isEqualTo("anthropic");
            assertThat(record.usage().inputTokens()).isEqualTo(11);
            assertThat(record.usage().outputTokens()).isEqualTo(7);
            assertThat(record.outcome().name()).isEqualTo("OK");
            assertThat(record.errorCode()).isNull();
        });
    }

    @Test
    void recordsTheNormalisedCodeRatherThanAVendorMessageOnFailure() {
        anthropic.failuresBeforeSuccess = 99;
        anthropic.failure = AiProviderException.rateLimited("anthropic");

        assertThatThrownBy(() -> router(Map.of(), "anthropic").complete(prompt(), LlmOptions.defaults()))
                .isInstanceOf(AiProviderException.class);

        assertThat(callLog.records).singleElement().satisfies(record -> {
            assertThat(record.outcome().name()).isEqualTo("ERROR");
            assertThat(record.errorCode()).isEqualTo("ai_rate_limited");
        });
    }

    /** The no-PII guarantee, asserted rather than assumed. */
    @Test
    void recordsAHashOfThePromptAndNeverThePromptText() {
        router(Map.of(), "anthropic").complete(
                Prompt.adHoc(List.of(PromptMessage.user("Two weeks in Tokyo with my partner"))),
                LlmOptions.defaults());

        AiCallRecord record = callLog.records.get(0);
        assertThat(record.promptHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(record.toString()).doesNotContain("Tokyo").doesNotContain("partner");
    }

    // ---------------------------------------------------------------------------------------
    // Streaming (ADR 007)
    // ---------------------------------------------------------------------------------------

    @Test
    void passesProviderEventsThroughUnchanged() {
        StepVerifier.create(router(Map.of(), "anthropic").stream(prompt(), LlmOptions.defaults()))
                .expectNext(new LlmEvent.TextDelta("ok"))
                .expectNext(new LlmEvent.Usage(11, 7, 0))
                .expectNext(new LlmEvent.Done(StopReason.END_TURN))
                .verifyComplete();
    }

    @Test
    void recordsAStreamedTurnWithTheUsageItSawAlongTheWay() {
        router(Map.of(), "anthropic").stream(prompt(), LlmOptions.defaults()).blockLast();

        assertThat(callLog.records).singleElement().satisfies(record -> {
            assertThat(record.operation().name()).isEqualTo("STREAM");
            assertThat(record.usage().inputTokens()).isEqualTo(11);
            assertThat(record.outcome().name()).isEqualTo("OK");
        });
    }

    /**
     * A user closing a browser tab must not read as an outage. Cancellation is recorded as
     * {@code CANCELLED}, and Reactor propagates it into the adapter so the provider call stops.
     */
    @Test
    void recordsAnAbandonedStreamAsCancelledRatherThanFailed() {
        StepVerifier.create(router(Map.of(), "anthropic").stream(prompt(), LlmOptions.defaults()), 1)
                .expectNext(new LlmEvent.TextDelta("ok"))
                .thenCancel()
                .verify();

        assertThat(callLog.records).singleElement()
                .satisfies(record -> assertThat(record.outcome().name()).isEqualTo("CANCELLED"));
    }

    @Test
    void recordsAMidStreamErrorFrameAsAFailureWithoutErroringTheFlux() {
        anthropic.streamError = LlmEvent.StreamError.of("ai_unavailable", "provider fell over");

        StepVerifier.create(router(Map.of(), "anthropic").stream(prompt(), LlmOptions.defaults()))
                .expectNext(new LlmEvent.TextDelta("ok"))
                .expectNext(anthropic.streamError)
                .verifyComplete();

        assertThat(callLog.records).singleElement().satisfies(record -> {
            assertThat(record.outcome().name()).isEqualTo("ERROR");
            assertThat(record.errorCode()).isEqualTo("ai_unavailable");
        });
    }

    /** Retry must not wrap a stream: the consumer has already rendered the first attempt's tokens. */
    @Test
    void neverRetriesAStream() {
        anthropic.streamError = LlmEvent.StreamError.of("ai_unavailable", "down");

        router(Map.of(), "anthropic").stream(prompt(), LlmOptions.defaults()).blockLast();

        assertThat(anthropic.streamCalls.get()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------

    private LlmClientRouter router(Map<String, String> routing, String defaultProvider) {
        AiProperties.Resilience resilience = new AiProperties.Resilience();
        CircuitBreakerGate breaker = new CountingCircuitBreakerGate(resilience,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));
        AiRetryPolicy retry = new AiRetryPolicy(resilience, duration -> { });
        return new LlmClientRouter(
                new RoutingTable(Map.of("anthropic", anthropic, "openai", openai), routing,
                        defaultProvider),
                new RouterSupport(retry, breaker, new AiCallRecorder(callLog, Clock.systemUTC())));
    }

    private static Prompt prompt() {
        return Prompt.adHoc(List.of(PromptMessage.user("plan something")));
    }

    /** A port double: no network, no LangChain4j, and a failure script the test controls. */
    private static final class FakeLlm implements LlmPort {

        private final String name;
        private final AtomicInteger completeCalls = new AtomicInteger();
        private final AtomicInteger streamCalls = new AtomicInteger();
        private int failuresBeforeSuccess;
        private AiProviderException failure;
        private LlmEvent.StreamError streamError;

        private FakeLlm(String name) {
            this.name = name;
        }

        @Override
        public String providerName() {
            return name;
        }

        @Override
        public String complete(Prompt prompt, LlmOptions options) {
            return completeWithTools(prompt, List.of(), options).text();
        }

        @Override
        public <T> T completeStructured(Prompt prompt, Class<T> type, LlmOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools,
                LlmOptions options) {
            if (completeCalls.incrementAndGet() <= failuresBeforeSuccess) {
                throw failure;
            }
            return new LlmCompletion("ok", List.of(), new LlmEvent.Usage(11, 7, 0),
                    StopReason.END_TURN);
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
            return stream(prompt, List.of(), options);
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
            return Flux.defer(() -> {
                streamCalls.incrementAndGet();
                if (streamError != null) {
                    return Flux.just(new LlmEvent.TextDelta("ok"), streamError);
                }
                return Flux.just(new LlmEvent.TextDelta("ok"), new LlmEvent.Usage(11, 7, 0),
                        new LlmEvent.Done(StopReason.END_TURN));
            });
        }
    }

    private static final class RecordingCallLog implements AiCallLogPort {

        private final List<AiCallRecord> records = new ArrayList<>();

        @Override
        public void record(AiCallRecord record) {
            records.add(record);
        }
    }
}
