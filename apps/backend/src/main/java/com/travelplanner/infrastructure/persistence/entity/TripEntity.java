package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.TripStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code trip} (V5).
 *
 * <p>{@code version} is a primitive {@code int} on purpose. Spring Data's
 * {@code JpaMetamodelEntityInformation} only uses a version property for new-entity detection when
 * that property is nullable; with a primitive it falls back to the id, which is always assigned
 * here, so {@code save} consistently takes the {@code merge} path. Consistency matters more than
 * the extra {@code SELECT}: a mix of {@code persist} and {@code merge} depending on field state is
 * how "sometimes it inserts a duplicate" bugs start.
 */
@Entity
@Table(name = "trip")
public class TripEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TripStatus status;

    @Column(name = "selected_recommendation_id")
    private UUID selectedRecommendationId;

    /** ADR 008 §1 — the optimistic lock the agent-versus-user race depends on. */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mapper in the sibling package, which is why it is public. */
    public TripEntity() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public UUID getSelectedRecommendationId() {
        return selectedRecommendationId;
    }

    public void setSelectedRecommendationId(UUID selectedRecommendationId) {
        this.selectedRecommendationId = selectedRecommendationId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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
