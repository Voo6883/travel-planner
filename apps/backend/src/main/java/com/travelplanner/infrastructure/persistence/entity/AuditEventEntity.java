package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code audit_event} (V12, PLAN §4.0.6).
 *
 * <p>No MapStruct mapper, matching {@link AiCallLogEntity}, {@link MailRateLimitEntity}, and
 * {@link LoginAttemptEntity}: the translation is a flat field copy in one direction, and generating
 * it would add a mapper interface whose whole body is what the adapter already reads in ten lines.
 *
 * <p><strong>There is no password, hash, email, or note field, and adding one would require
 * changing this class, the domain record, the adapter, and a migration.</strong> That is the
 * enforcement mechanism for the table's PII rule — a shape, not a discipline.
 *
 * <p>{@code action} and {@code result} are stored as {@code String} rather than
 * {@code @Enumerated}. Both columns already carry a CHECK constraint that is the real arbiter, and
 * a Hibernate enum binding would turn an unrecognised historical value — the one an audit table is
 * most likely to meet years later — into a read failure for the whole page.
 *
 * <p>No {@code @Version}. The table is append-only; nothing ever updates a row, and an audit record
 * that could be revised would not be one.
 */
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "result", nullable = false, length = 16)
    private String result;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    public AuditEventEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(UUID targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
