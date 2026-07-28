package com.travelplanner.domain.ai;

import java.util.Objects;

/**
 * One turn in a conversation handed to a model.
 *
 * @param toolCallId set only for {@link MessageRole#TOOL}; {@code null} otherwise. It correlates the
 *     result with the {@code ToolUseStart} that requested it — a tool result with no id cannot be
 *     matched to its call, and providers reject the request rather than guess.
 */
public record PromptMessage(MessageRole role, String text, String toolCallId) {

    public PromptMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
        if (role == MessageRole.TOOL && (toolCallId == null || toolCallId.isBlank())) {
            throw new IllegalArgumentException("A TOOL message requires a toolCallId");
        }
        if (role != MessageRole.TOOL && toolCallId != null) {
            throw new IllegalArgumentException("Only a TOOL message may carry a toolCallId");
        }
    }

    public static PromptMessage system(String text) {
        return new PromptMessage(MessageRole.SYSTEM, text, null);
    }

    public static PromptMessage user(String text) {
        return new PromptMessage(MessageRole.USER, text, null);
    }

    public static PromptMessage assistant(String text) {
        return new PromptMessage(MessageRole.ASSISTANT, text, null);
    }

    public static PromptMessage toolResult(String toolCallId, String payload) {
        return new PromptMessage(MessageRole.TOOL, payload, toolCallId);
    }
}
