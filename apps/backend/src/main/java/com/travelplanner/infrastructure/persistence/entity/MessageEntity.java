package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code message} (V19).
 *
 * <p><strong>{@link #getContent()} holds user-visible content only.</strong> No column here can
 * carry a provider reasoning trace, and none may be added — tasks/20's Definition of Done forbids
 * storing hidden chain-of-thought, and the absence of a field is what makes that structural rather
 * than a rule somebody has to remember.
 *
 * <p>{@code conversationId} is a raw {@code uuid} rather than a {@code @ManyToOne}. A history page
 * loads up to a few hundred of these; an association would attach the same conversation to every
 * one of them and make the ownership filter look like a free property access instead of the join it
 * is. The repository writes that join explicitly, which keeps the {@code user_id} predicate PLAN
 * §4.0.2-L requires visible in the query text.
 *
 * <p>{@code seq} is a {@code long} mapped to {@code bigint}. An {@code int} would cap a thread at
 * two billion messages, which is not a real limit — but the column is also the ADR 007 SSE frame
 * id, and a wrapped frame id breaks resume rather than merely truncating history.
 */
@Entity
@Table(name = "message")
public class MessageEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "seq", nullable = false)
    private long seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 24)
    private ChatMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChatMessageStatus status;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Required by JPA, and by the mapper in the sibling package, which is why it is public. */
    public MessageEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public void setRole(ChatMessageRole role) {
        this.role = role;
    }

    public ChatMessageStatus getStatus() {
        return status;
    }

    public void setStatus(ChatMessageStatus status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
