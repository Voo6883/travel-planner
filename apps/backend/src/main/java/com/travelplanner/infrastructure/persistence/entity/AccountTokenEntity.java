package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.AccountTokenPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row mapping for {@code account_token} (V8). Never leaves {@code infrastructure.persistence}.
 *
 * <p>{@code tokenHash} is the SHA-256 digest, never the token — the raw value exists only in the
 * mailed link. {@code userId} is a plain column rather than an association, matching
 * {@link RefreshTokenEntity}: the link is only ever navigated from a known user id, and an
 * association would invite a lazy load from outside a transaction.
 */
@Entity
@Table(name = "account_token")
public class AccountTokenEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // STRING, never ORDINAL — an ordinal makes the persisted meaning depend on declaration order,
    // and V8's CHECK constraint lists the names.
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private AccountTokenPurpose purpose;

    // `char(64)`, not `varchar`: V8 declared a fixed-width digest and `ddl-auto: validate` compares
    // the JDBC type name. Without this the application refuses to start against its own schema.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA, and by the mapper in the sibling package, which is why it is public. */
    public AccountTokenEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public AccountTokenPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(AccountTokenPurpose purpose) {
        this.purpose = purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
