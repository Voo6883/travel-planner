package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code user_identity} (V3).
 *
 * <p>{@code userId} is a plain column rather than a {@code @ManyToOne} association. The link is
 * only ever navigated from a known user id, and an association would invite lazy loading from
 * outside a transaction plus an N+1 on the "list my linked providers" query (UC-A11), for no gain.
 */
@Entity
@Table(name = "user_identity")
public class UserIdentityEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private AuthProvider provider;

    @Column(name = "provider_subject_id", nullable = false, length = 255)
    private String providerSubjectId;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA, and by the mapper in the sibling package, which is why it is public. */
    public UserIdentityEntity() {
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

    public AuthProvider getProvider() {
        return provider;
    }

    public void setProvider(AuthProvider provider) {
        this.provider = provider;
    }

    public String getProviderSubjectId() {
        return providerSubjectId;
    }

    public void setProviderSubjectId(String providerSubjectId) {
        this.providerSubjectId = providerSubjectId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
