package com.travelplanner.domain.ai;

import java.util.List;
import java.util.Objects;

/**
 * The result of a non-streaming call.
 *
 * <p>Naming note: PLAN §5.1 sketches {@code ToolResult completeWithTools(...)}. That name is taken
 * here by {@code LlmEvent.ToolResult}, which means something different — the outcome of the
 * orchestrator <em>executing</em> a tool, not the model <em>requesting</em> one. Keeping one name
 * for two concepts in the same package would guarantee a mix-up at the exact boundary where a
 * mistake executes something, so the non-streaming result is {@code LlmCompletion} and the model's
 * requests are {@link LlmToolCall}.
 *
 * @param usage always present — {@link LlmEvent.Usage#none()} when the provider reported nothing, so
 *     {@code ai_call_log} never has to null-check
 */
public record LlmCompletion(String text, List<LlmToolCall> toolCalls, LlmEvent.Usage usage,
        StopReason stopReason) {

    public LlmCompletion {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? LlmEvent.Usage.none() : usage;
        Objects.requireNonNull(stopReason, "stopReason");
    }

    public static LlmCompletion ofText(String text, LlmEvent.Usage usage) {
        return new LlmCompletion(text, List.of(), usage, StopReason.END_TURN);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
