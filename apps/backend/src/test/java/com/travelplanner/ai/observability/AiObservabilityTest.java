package com.travelplanner.ai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.ai.AiCallRecord;
import com.travelplanner.domain.ai.AiOperation;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.port.AiCallLogPort;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** The {@code ai_call_log} pipeline, and the guarantee that it never carries a prompt or PII. */
class AiObservabilityTest {

    private final List<AiCallRecord> recorded = new ArrayList<>();
    private final AiCallLogPort callLog = recorded::add;
    private final AiCallRecorder recorder = new AiCallRecorder(callLog, Clock.systemUTC());

    // ---------------------------------------------------------------------------------------
    // No prompts, no PII (AI-AGENT-WORKFLOW A4, PLAN §9)
    // ---------------------------------------------------------------------------------------

    /**
     * The strongest form of the guarantee: the record has no component that could hold prompt or
     * completion text, so logging one would require changing the type — not just remembering not to.
     */
    @Test
    void hasNoFieldCapableOfHoldingPromptOrCompletionText() {
        List<String> componentNames = java.util.Arrays.stream(AiCallRecord.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames).doesNotContain("prompt", "promptText", "completion", "response",
                "messages", "text", "email", "content");
        assertThat(componentNames).contains("promptHash");
    }

    @Test
    void reducesAPromptToAHexDigestThatDoesNotContainItsWords() {
        String hash = PromptHasher.hash(Prompt.adHoc(List.of(
                PromptMessage.user("Two weeks in Tokyo with Aisyah, budget MYR 8000"))));

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}")
                .doesNotContain("Tokyo").doesNotContain("Aisyah");
    }

    @Test
    void hashesEqualPromptsEquallyAndDifferentPromptsDifferently() {
        Prompt first = Prompt.adHoc(List.of(PromptMessage.user("Tokyo")));
        Prompt second = Prompt.adHoc(List.of(PromptMessage.user("Tokyo")));
        Prompt third = Prompt.adHoc(List.of(PromptMessage.user("Bangkok")));

        assertThat(PromptHasher.hash(first)).isEqualTo(PromptHasher.hash(second));
        assertThat(PromptHasher.hash(first)).isNotEqualTo(PromptHasher.hash(third));
    }

    /** A prompt's version is part of its identity: "which version regressed" is the whole question. */
    @Test
    void distinguishesTwoVersionsOfTheSameTemplate() {
        List<PromptMessage> messages = List.of(PromptMessage.user("same"));

        assertThat(PromptHasher.hash(new Prompt("brief", 1, messages)))
                .isNotEqualTo(PromptHasher.hash(new Prompt("brief", 2, messages)));
    }

    /** A different split of the same characters must not collide. */
    @Test
    void separatesMessagesSoAReSplitConversationDoesNotCollide() {
        Prompt joined = Prompt.adHoc(List.of(PromptMessage.user("abcdef")));
        Prompt split = Prompt.adHoc(List.of(PromptMessage.user("abc"), PromptMessage.user("def")));

        assertThat(PromptHasher.hash(joined)).isNotEqualTo(PromptHasher.hash(split));
    }

    // ---------------------------------------------------------------------------------------
    // The recorder
    // ---------------------------------------------------------------------------------------

    @Test
    void carriesTheRequestIdFromTheMdcSoACallCanBeTracedToItsHttpRequest() {
        MDC.put("requestId", "req-42");
        try {
            recorder.recordSuccess(context(), new LlmEvent.Usage(10, 5, 0), 120L);
        } finally {
            MDC.remove("requestId");
        }

        assertThat(recorded).singleElement()
                .satisfies(record -> assertThat(record.requestId()).isEqualTo("req-42"));
    }

    @Test
    void recordsTokensLatencyProviderAndModel() {
        recorder.recordSuccess(context(), new LlmEvent.Usage(100, 40, 20), 850L);

        assertThat(recorded).singleElement().satisfies(record -> {
            assertThat(record.usage().inputTokens()).isEqualTo(100);
            assertThat(record.usage().cachedTokens()).isEqualTo(20);
            assertThat(record.latencyMs()).isEqualTo(850L);
            assertThat(record.provider()).isEqualTo("anthropic");
            assertThat(record.model()).isEqualTo("claude-sonnet-4-5");
        });
    }

    /** Observability must never be able to fail a user's request. */
    @Test
    void swallowsAFailingCallLogRatherThanFailingTheCall() {
        AiCallRecorder failing = new AiCallRecorder(record -> {
            throw new IllegalStateException("database is down");
        }, Clock.systemUTC());

        failing.recordSuccess(context(), LlmEvent.Usage.none(), 10L);
    }

    // ---------------------------------------------------------------------------------------
    // Cost (PLAN §5.3, §13.1)
    // ---------------------------------------------------------------------------------------

    @Test
    void estimatesCostAsBigDecimalNeverAFloatingPointType() {
        BigDecimal cost = AiCostEstimator.estimate("claude-sonnet-4-5",
                new LlmEvent.Usage(1_000_000, 0, 0));

        assertThat(cost).isEqualByComparingTo("3.00");
    }

    /** Ignoring the cache discount overstates a long conversation several-fold. */
    @Test
    void billsCachedInputTokensAtADiscount() {
        BigDecimal uncached = AiCostEstimator.estimate("claude-sonnet-4-5",
                new LlmEvent.Usage(1_000_000, 0, 0));
        BigDecimal cached = AiCostEstimator.estimate("claude-sonnet-4-5",
                new LlmEvent.Usage(1_000_000, 0, 1_000_000));

        assertThat(cached).isLessThan(uncached).isEqualByComparingTo("0.30");
    }

    /** A wrong number in a cost dashboard is worse than a visibly missing one. */
    @Test
    void reportsZeroForAModelItHasNoPriceFor() {
        assertThat(AiCostEstimator.estimate("some-new-model", new LlmEvent.Usage(1000, 1000, 0)))
                .isEqualByComparingTo("0");
    }

    private static AiCallContext context() {
        return AiCallContext.of("chat", AiOperation.COMPLETE, "anthropic")
                .withModel("claude-sonnet-4-5")
                .withPromptHash(PromptHasher.hash(Prompt.adHoc(List.of(PromptMessage.user("hi")))));
    }
}
