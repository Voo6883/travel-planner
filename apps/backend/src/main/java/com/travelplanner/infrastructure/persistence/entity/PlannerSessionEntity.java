package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code planner_session} (V19).
 *
 * <p>No {@code @Version}. ADR 008 §1 scopes optimistic locking to the agent-mutable trip
 * aggregates; a planner session has exactly one lifecycle write — {@code ended_at} — and two
 * concurrent handoffs are prevented by {@code uq_planner_session_user_open}, at the level where
 * the race actually happens, rather than by a column the loser would have to retry against.
 */
@Entity
@Table(name = "planner_session")
public class PlannerSessionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mapper in the sibling package, which is why it is public. */
    public PlannerSessionEntity() {
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

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
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
