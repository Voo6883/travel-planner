package com.travelplanner.domain.ai;

/**
 * Why a turn ended, normalised across providers (ADR 007 — {@code Done(stopReason)}).
 *
 * <p>Provider-neutral by design: Anthropic says {@code end_turn} / {@code max_tokens} /
 * {@code tool_use}, OpenAI says {@code stop} / {@code length} / {@code tool_calls}. Callers must not
 * branch on vendor strings, so the mapping happens once, inside {@code ai/langchain4j/}.
 *
 * <p>The distinction that matters to the product is {@link #TOOL_USE} versus everything else: a turn
 * that stopped to call a tool is <em>not</em> finished, and rendering it as a completed assistant
 * message is how a half-answered question reaches the user.
 */
public enum StopReason {

    /** The model finished what it had to say. */
    END_TURN,

    /**
     * The output token cap was reached. The text is truncated mid-thought — never persist it as a
     * complete structured result.
     */
    MAX_TOKENS,

    /** The model wants a tool executed. The orchestrator runs it and continues the loop. */
    TOOL_USE,

    /** A configured stop sequence matched. */
    STOP_SEQUENCE,

    /** The provider's safety filter suppressed the output. */
    CONTENT_FILTERED,

    /**
     * The caller cancelled — the user navigated away, or the SSE connection dropped and the agent
     * run was cancelled with it (ADR 007, Cancellation).
     */
    CANCELLED,

    /** The provider reported a reason this enum does not model. Logged, never branched on. */
    OTHER
}
