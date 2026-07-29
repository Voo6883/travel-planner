package com.travelplanner.domain.enums;

/**
 * What a stored chat turn element is (tasks/20: "Message roles/types sufficient for user,
 * assistant, system metadata, tool calls/results, and lifecycle events without storing hidden
 * chain-of-thought").
 *
 * <p><strong>There is no {@code REASONING} constant, and there must never be one.</strong> The
 * tasks/20 Definition of Done forbids storing hidden chain-of-thought, and this enum is where that
 * ban is cheapest to enforce: {@code ck_message_role} lists exactly these names, so a provider
 * reasoning trace has no role it could be persisted under. Adding one would mean changing this
 * file, the migration, and the test that asserts they agree.
 *
 * <p>The names are the persisted values ({@code message.role varchar} with {@code ck_message_role})
 * and the wire values, so they are a contract in three places at once — the reason
 * {@code MigrationContractTest} asserts the set instead of trusting review.
 *
 * <p><strong>Why the {@code Chat} prefix.</strong> {@code domain/ai/MessageRole} already exists and
 * means something else: the four provider-neutral roles of a {@code PromptMessage} (task 14,
 * ADR 007). This one is the six-value <em>persistence</em> vocabulary. The context assembly of
 * task 21/22 has to hold both at once while turning stored history into model input, and two types
 * with the same simple name would force that file to fully-qualify one of them — which is how the
 * wrong constant eventually gets used. {@link ChatMessageStatus} takes the prefix for symmetry, so
 * the chat vocabulary reads as one group (PLAN §8 lists these tables under "Chat:").
 */
public enum ChatMessageRole {

    /** What the user sent. The only role a client may author, and the only one that is retryable. */
    USER,

    /** What the model produced — the visible answer text only, never how it got there. */
    ASSISTANT,

    /** System metadata surfaced in the thread: a status note, a policy notice, a summary marker. */
    SYSTEM,

    /** ADR 007 {@code ToolUseStart}/{@code ToolInputDelta}: the agent invoked a tool. */
    TOOL_CALL,

    /** ADR 007 {@code ToolResult}: what the tool returned, correlated by {@code toolCallId}. */
    TOOL_RESULT,

    /**
     * ADR 007 {@code DomainEvent} — a thing that happened to the trip, recorded in the thread so a
     * reload shows it. {@code trip_created} is the first one (PLAN §3.2 Handoff).
     */
    LIFECYCLE_EVENT;

    /** Only a client-authored message can be re-sent, so only this role carries a retry key. */
    public boolean acceptsClientMessageId() {
        return this == USER;
    }

    /** {@code ck_message_tool_call_id_paired}: exactly these two roles carry a {@code toolCallId}. */
    public boolean carriesToolCorrelation() {
        return this == TOOL_CALL || this == TOOL_RESULT;
    }

    /** A tool invocation must name the tool it invoked, or it cannot be rendered or audited. */
    public boolean requiresToolName() {
        return this == TOOL_CALL;
    }

    /**
     * Whether this role is produced incrementally. Only an assistant message streams; everything
     * else is written once, complete, which is why nothing else can be {@code INTERRUPTED}.
     */
    public boolean isStreamable() {
        return this == ASSISTANT;
    }
}
