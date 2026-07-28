package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code login_attempt} (V7) — one failed sign-in.
 *
 * <p>There is no domain model behind this entity and no MapStruct mapper. The port speaks in
 * counts and instants, so a domain record would exist only to be counted, and the whole table is a
 * sliding window rather than part of any aggregate.
 */
@Entity
@Table(name = "login_attempt")
public class LoginAttemptEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Lower-cased before it reaches here; the migration has a CHECK constraint that says so. */
    @Column(name = "login_identifier", nullable = false, length = 320)
    private String loginIdentifier;

    @Column(name = "client_ip", nullable = false, length = 45)
    private String clientIp;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    /** Required by JPA. */
    public LoginAttemptEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getLoginIdentifier() {
        return loginIdentifier;
    }

    public void setLoginIdentifier(String loginIdentifier) {
        this.loginIdentifier = loginIdentifier;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }
}
