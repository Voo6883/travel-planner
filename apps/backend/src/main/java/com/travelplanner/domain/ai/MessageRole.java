package com.travelplanner.domain.ai;

/** Who authored a {@link PromptMessage}. Provider-neutral; adapters translate to vendor roles. */
public enum MessageRole {

    /**
     * Instructions and retrieved context. Both providers treat this differently on the wire —
     * Anthropic carries it as a top-level {@code system} field, OpenAI as the first message — which
     * is precisely why callers must not assemble vendor payloads themselves.
     */
    SYSTEM,

    /** Text originating from the person. Always untrusted (PLAN §9) and sanitised before use. */
    USER,

    /** A previous assistant turn, replayed as conversation history. */
    ASSISTANT,

    /** The result of a tool the orchestrator executed; must carry a {@code toolCallId}. */
    TOOL
}
