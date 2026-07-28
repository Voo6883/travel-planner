package com.travelplanner.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The ADR 007 union: closed, exhaustive, and defensive about the payloads it hands on. */
class LlmEventTest {

    @Test
    void isSealedToExactlyTheNineVariantsAdr007Lists() {
        assertThat(LlmEvent.class.isSealed()).isTrue();
        assertThat(LlmEvent.class.getPermittedSubclasses())
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("TextDelta", "ToolUseStart", "ToolInputDelta",
                        "ToolUseEnd", "ToolResult", "DomainEvent", "Usage", "Done", "StreamError");
    }

    /**
     * The point of sealing: a {@code switch} over the union needs no {@code default}, so adding a
     * tenth variant becomes a compile error in every consumer rather than a frame the frontend
     * silently drops.
     */
    @Test
    void supportsExhaustiveSwitchWithoutADefaultBranch() {
        List<LlmEvent> events = List.of(
                new LlmEvent.TextDelta("hi"),
                new LlmEvent.ToolUseStart("call_1", "create_trip"),
                new LlmEvent.ToolInputDelta("call_1", "{\"name\":"),
                new LlmEvent.ToolUseEnd("call_1"),
                new LlmEvent.ToolResult("call_1", "{\"trip_id\":\"t1\"}"),
                new LlmEvent.DomainEvent("trip_created", Map.of("trip_id", "t1")),
                new LlmEvent.Usage(10, 5, 2),
                new LlmEvent.Done(StopReason.END_TURN),
                LlmEvent.StreamError.of("ai_unavailable", "down"));

        assertThat(events.stream().map(LlmEventTest::describe))
                .containsExactly("text", "tool-start", "tool-input", "tool-end", "tool-result",
                        "domain", "usage", "done", "error");
    }

    private static String describe(LlmEvent event) {
        return switch (event) {
            case LlmEvent.TextDelta ignored -> "text";
            case LlmEvent.ToolUseStart ignored -> "tool-start";
            case LlmEvent.ToolInputDelta ignored -> "tool-input";
            case LlmEvent.ToolUseEnd ignored -> "tool-end";
            case LlmEvent.ToolResult ignored -> "tool-result";
            case LlmEvent.DomainEvent ignored -> "domain";
            case LlmEvent.Usage ignored -> "usage";
            case LlmEvent.Done ignored -> "done";
            case LlmEvent.StreamError ignored -> "error";
        };
    }

    @Test
    void copiesDomainEventPayloadSoACallerCannotMutateAnEmittedFrame() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("trip_id", "t1");
        LlmEvent.DomainEvent event = new LlmEvent.DomainEvent("trip_created", mutable);

        mutable.put("trip_id", "tampered");

        assertThat(event.payload()).containsEntry("trip_id", "t1");
    }

    @Test
    void treatsANullPayloadAsEmptySoConsumersNeverNullCheck() {
        assertThat(new LlmEvent.DomainEvent("trip_created", null).payload()).isEmpty();
        assertThat(new LlmEvent.StreamError("ai_timeout", "slow", null).details()).isEmpty();
    }

    @Test
    void clampsNegativeTokenCountsRatherThanPropagatingThemIntoCostArithmetic() {
        LlmEvent.Usage usage = new LlmEvent.Usage(-5, -1, -3);

        assertThat(usage.inputTokens()).isZero();
        assertThat(usage.outputTokens()).isZero();
        assertThat(usage.cachedTokens()).isZero();
    }

    @Test
    void countsCachedTokensInsideTheInputTotalNotOnTopOfIt() {
        assertThat(new LlmEvent.Usage(100, 20, 80).totalTokens()).isEqualTo(120);
    }

    @Test
    void rejectsAnEventBuiltWithoutItsCorrelationId() {
        assertThatThrownBy(() -> new LlmEvent.ToolUseStart(null, "create_trip"))
                .isInstanceOf(NullPointerException.class);
    }
}
