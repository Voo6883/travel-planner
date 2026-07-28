package com.travelplanner.domain.ai;

import java.util.Objects;

/**
 * A completed tool-call request from a model — the assembled form of the
 * {@code ToolUseStart}/{@code ToolInputDelta}/{@code ToolUseEnd} sequence, for callers that do not
 * stream.
 *
 * @param argumentsJson raw, unvalidated model output. Treat as untrusted (PLAN §9): it must be
 *     validated against the tool's {@link ToolSpec#parametersJsonSchema()} before anything executes.
 */
public record LlmToolCall(String toolCallId, String name, String argumentsJson) {

    public LlmToolCall {
        Objects.requireNonNull(toolCallId, "toolCallId");
        Objects.requireNonNull(name, "name");
        argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
    }
}
