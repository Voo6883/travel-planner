package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row mapping for {@code mail_rate_limit} (V9) — one accepted mail request.
 *
 * <p>No domain model and no MapStruct mapper, for the same reason {@link LoginAttemptEntity} has
 * none: the port speaks in counts and instants, so a domain record would exist only to be counted,
 * and the table is a sliding window rather than part of any aggregate.
 *
 * <p>{@code subjectHash} is a digest of the email address or the client IP. The plaintext never
 * reaches this table — see V9 and {@code MailRateLimitPort}.
 */
@Entity
@Table(name = "mail_rate_limit")
public class MailRateLimitEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Lower-case; V9 has a CHECK constraint that says so. */
    @Column(name = "scope", nullable = false, length = 64)
    private String scope;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "subject_hash", nullable = false, length = 64)
    private String subjectHash;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    /** Required by JPA. */
    public MailRateLimitEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getSubjectHash() {
        return subjectHash;
    }

    public void setSubjectHash(String subjectHash) {
        this.subjectHash = subjectHash;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }
}
