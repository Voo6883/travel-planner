package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One durable element of a chat turn (PLAN §8 "Chat: … {@code message}").
 *
 * <p><strong>{@link #content()} is user-visible content only.</strong> The tasks/20 Definition of
 * Done forbids storing hidden chain-of-thought and its Do-not list forbids exposing raw provider
 * events, so a provider reasoning trace must never be written here — and cannot be labelled if it
 * were, because {@link ChatMessageRole} has no constant for it. Only ADR 007 {@code TextDelta} text and
 * {@code ToolResult} payloads reach this record.
 *
 * <p><strong>{@link #seq()} orders the conversation, not {@link #createdAt()}.</strong> Postgres
 * fixes {@code now()} for a whole transaction, so every row one turn writes shares a timestamp
 * exactly; ordering on it is not approximate, it is undefined. {@code seq} is allocated from
 * {@link Conversation#nextMessageSeq()} and is unique per conversation, which is also what makes it
 * the ADR 007 resume cursor ({@code Last-Event-ID} → {@code WHERE seq > ?}).
 *
 * <p>Immutable. A streaming assistant message grows through {@link #appendContent} returning new
 * instances, and ends in exactly one terminal status — which is how tasks/20's "partial assistant
 * messages must be explicitly marked" is satisfied without a boolean anybody can forget to set.
 *
 * @param clientMessageId the retry key the client minted before sending. Present only on a
 *        {@link ChatMessageRole#USER} message, unique per conversation, and the reason a re-send after
 *        a dropped response cannot commit twice.
 * @param toolCallId ADR 007's {@code toolCallId} — what joins a {@link ChatMessageRole#TOOL_CALL} to
 *        the {@link ChatMessageRole#TOOL_RESULT} answering it. Provider-issued text, not a UUID.
 */
public record Message(
        UUID id,
        UUID conversationId,
        long seq,
        ChatMessageRole role,
        ChatMessageStatus status,
        String content,
        String clientMessageId,
        String toolCallId,
        String toolName,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    /** Matches {@code message.client_message_id varchar(64)}. */
    public static final int MAX_CLIENT_MESSAGE_ID_LENGTH = 64;

    /** Matches {@code message.tool_call_id varchar(64)} and {@code message.tool_name varchar(64)}. */
    public static final int MAX_TOOL_IDENTIFIER_LENGTH = 64;

    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        // Matches the column's NOT NULL DEFAULT ''. A streaming assistant row legitimately has no
        // text yet, and "" says that far more usefully than null, which every reader must guard.
        content = content == null ? "" : content;
        if (seq < Conversation.FIRST_SEQ) {
            throw ValidationFailedException.field("seq",
                    "must be at least " + Conversation.FIRST_SEQ);
        }
        // Mirrors ck_message_completed_at_matches_status. Terminal and timestamped are one fact.
        if (status.isTerminal() != (completedAt != null)) {
            throw ValidationFailedException.field("completed_at",
                    "must be present exactly when the message is no longer streaming");
        }
        // Stricter than the database on purpose: only an assistant message is produced
        // incrementally, so nothing else can be STREAMING — and therefore nothing else can be
        // INTERRUPTED either. A SYSTEM row left mid-stream would be a bug with no way to finish.
        if (status == ChatMessageStatus.STREAMING && !role.isStreamable()) {
            throw ValidationFailedException.field("status",
                    "only an assistant message streams");
        }
        clientMessageId = requireValidClientMessageId(role, clientMessageId);
        toolCallId = requireValidToolCallId(role, toolCallId);
        toolName = requireValidToolName(role, toolName);
    }

    /**
     * A user turn. Complete on arrival — a client sends a whole message, never a partial one.
     *
     * @param clientMessageId the caller's retry key; required, because a user message with no
     *        idempotency key is one a dropped response can duplicate
     */
    public static Message fromUser(UUID conversationId, long seq, String content,
            String clientMessageId, Instant now) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw ValidationFailedException.field("client_message_id", "must not be blank");
        }
        return new Message(UUID.randomUUID(), conversationId, seq, ChatMessageRole.USER,
                ChatMessageStatus.COMPLETE, content, clientMessageId, null, null, now, now, now);
    }

    /**
     * An empty assistant message opened at the start of a turn. Persisted before the first token so
     * that a reload mid-stream — or an ADR 007 resume — is served from the database rather than
     * from an in-memory buffer that a restart would lose.
     */
    public static Message assistantStreamStarted(UUID conversationId, long seq, Instant now) {
        return new Message(UUID.randomUUID(), conversationId, seq, ChatMessageRole.ASSISTANT,
                ChatMessageStatus.STREAMING, "", null, null, null, now, now, null);
    }

    /** System metadata surfaced in the thread — a status note, a policy notice. Never reasoning. */
    public static Message system(UUID conversationId, long seq, String content, Instant now) {
        return new Message(UUID.randomUUID(), conversationId, seq, ChatMessageRole.SYSTEM,
                ChatMessageStatus.COMPLETE, content, null, null, null, now, now, now);
    }

    /** ADR 007 {@code ToolUseStart}/{@code ToolInputDelta}, recorded once the input is complete. */
    public static Message toolCall(UUID conversationId, long seq, String toolCallId,
            String toolName, String inputJson, Instant now) {
        return new Message(UUID.randomUUID(), conversationId, seq, ChatMessageRole.TOOL_CALL,
                ChatMessageStatus.COMPLETE, inputJson, null, toolCallId, toolName, now, now, now);
    }

    /** ADR 007 {@code ToolResult}, correlated to its call by {@code toolCallId}. */
    public static Message toolResult(UUID conversationId, long seq, String toolCallId,
            String payload, Instant now) {
        return new Message(UUID.randomUUID(), conversationId, seq, ChatMessageRole.TOOL_RESULT,
                ChatMessageStatus.COMPLETE, payload, null, toolCallId, null, now, now, now);
    }

    /**
     * ADR 007 {@code DomainEvent} — something that happened to the trip, recorded in the thread so
     * a reload shows it. {@code trip_created} is the first (PLAN §3.2 Handoff).
     */
    public static Message lifecycleEvent(UUID conversationId, long seq, String payload, Instant now) {
        return new Message(UUID.randomUUID(), conversationId, seq, ChatMessageRole.LIFECYCLE_EVENT,
                ChatMessageStatus.COMPLETE, payload, null, null, null, now, now, now);
    }

    /** Absent on every server-authored message — only a client can retry a send. */
    public Optional<String> clientMessageIdIfPresent() {
        return Optional.ofNullable(clientMessageId);
    }

    /** Absent unless this is a tool call or a tool result. */
    public Optional<String> toolCallIdIfPresent() {
        return Optional.ofNullable(toolCallId);
    }

    /** Absent while the message is still streaming. */
    public Optional<Instant> completedAtIfPresent() {
        return Optional.ofNullable(completedAt);
    }

    /** True when this message and {@code other} are the same tool interaction (ADR 007). */
    public boolean correlatesWith(Message other) {
        return toolCallId != null && other != null && toolCallId.equals(other.toolCallId());
    }

    /**
     * True when this is the same send as {@code candidateClientMessageId} — the idempotency check
     * a retry resolves against before anything is committed.
     */
    public boolean hasClientMessageId(String candidateClientMessageId) {
        return clientMessageId != null && clientMessageId.equals(candidateClientMessageId);
    }

    /**
     * Appends a token delta to a streaming message (ADR 007: assistant messages are persisted
     * incrementally).
     *
     * @throws ValidationFailedException when the message has already ended. Writing to a terminal
     *         row would let a late frame from a cancelled run resurrect text the user was already
     *         told was cut short.
     */
    public Message appendContent(String delta, Instant now) {
        requireStreaming("content");
        String appended = delta == null ? content : content + delta;
        return new Message(id, conversationId, seq, role, status, appended, clientMessageId,
                toolCallId, toolName, createdAt, now, null);
    }

    /** The model reached a stop reason (ADR 007 {@code Done}). */
    public Message complete(Instant now) {
        return finish(ChatMessageStatus.COMPLETE, now);
    }

    /**
     * The client disconnected or cancelled. tasks/20 requires partial assistant messages to be
     * "explicitly marked or safely discarded"; ADR 007 chose marking, so whatever text arrived is
     * kept and labelled rather than thrown away.
     */
    public Message interrupt(Instant now) {
        return finish(ChatMessageStatus.INTERRUPTED, now);
    }

    /** The stream ended on an ADR 007 {@code StreamError}. Text received before it is kept. */
    public Message fail(Instant now) {
        return finish(ChatMessageStatus.FAILED, now);
    }

    private Message finish(ChatMessageStatus terminal, Instant now) {
        requireStreaming("status");
        return new Message(id, conversationId, seq, role, terminal, content, clientMessageId,
                toolCallId, toolName, createdAt, now, now);
    }

    private void requireStreaming(String field) {
        if (status.isTerminal()) {
            throw ValidationFailedException.field(field,
                    "the message has already ended with status " + status);
        }
    }

    private static String requireValidClientMessageId(ChatMessageRole role, String clientMessageId) {
        if (clientMessageId == null) {
            return null;
        }
        String trimmed = clientMessageId.trim();
        if (trimmed.isEmpty()) {
            throw ValidationFailedException.field("client_message_id", "must not be blank");
        }
        // Mirrors ck_message_client_id_is_user_only. A retry key on a generated message would
        // invite a "resume" path that re-commits model output under an id the client never issued.
        if (!role.acceptsClientMessageId()) {
            throw ValidationFailedException.field("client_message_id",
                    "only a user message may carry a client message id");
        }
        if (trimmed.length() > MAX_CLIENT_MESSAGE_ID_LENGTH) {
            throw ValidationFailedException.field("client_message_id",
                    "must be at most " + MAX_CLIENT_MESSAGE_ID_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String requireValidToolCallId(ChatMessageRole role, String toolCallId) {
        // Mirrors ck_message_tool_call_id_paired — an equivalence, so an orphan id on an assistant
        // row is refused as firmly as a tool result with nothing to correlate to.
        if (role.carriesToolCorrelation() != (toolCallId != null)) {
            throw ValidationFailedException.field("tool_call_id",
                    "must be present exactly on a tool call or a tool result");
        }
        if (toolCallId != null && toolCallId.length() > MAX_TOOL_IDENTIFIER_LENGTH) {
            throw ValidationFailedException.field("tool_call_id",
                    "must be at most " + MAX_TOOL_IDENTIFIER_LENGTH + " characters");
        }
        return toolCallId;
    }

    private static String requireValidToolName(ChatMessageRole role, String toolName) {
        // Mirrors ck_message_tool_name_present. A tool invocation whose tool is unknown cannot be
        // rendered in the thread or answered for in an audit.
        if (role.requiresToolName() && (toolName == null || toolName.isBlank())) {
            throw ValidationFailedException.field("tool_name",
                    "a tool call must name the tool it invoked");
        }
        if (toolName != null && toolName.length() > MAX_TOOL_IDENTIFIER_LENGTH) {
            throw ValidationFailedException.field("tool_name",
                    "must be at most " + MAX_TOOL_IDENTIFIER_LENGTH + " characters");
        }
        return toolName;
    }
}
