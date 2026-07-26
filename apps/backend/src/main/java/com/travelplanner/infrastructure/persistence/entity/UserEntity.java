package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code "user"} (V2). Never leaves {@code infrastructure.persistence}.
 *
 * <p>The table name is quoted because {@code user} is a SQL reserved word. Hibernate needs the
 * escape in the annotation as well as in the DDL, or it emits {@code from user} and PostgreSQL
 * parses it as the {@code CURRENT_USER} function.
 *
 * <p>No {@code @Version} here: ADR 008 §1 lists the aggregates that get optimistic locking and
 * {@code user} is not one of them. {@code tokenVersion} is unrelated — it is the ADR 009
 * revocation counter, deliberately bumped rather than compared.
 */
@Entity
@Table(name = "\"user\"")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // STRING, never ORDINAL. An ordinal makes the persisted meaning depend on declaration order,
    // so inserting a constant silently reinterprets every existing row.
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "sessions_valid_after")
    private Instant sessionsValidAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mapper in the sibling package, which is why it is public. */
    public UserEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public Instant getSessionsValidAfter() {
        return sessionsValidAfter;
    }

    public void setSessionsValidAfter(Instant sessionsValidAfter) {
        this.sessionsValidAfter = sessionsValidAfter;
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
}
