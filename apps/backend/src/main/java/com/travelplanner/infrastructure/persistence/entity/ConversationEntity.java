package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.ConversationScope;
import com.travelplanner.domain.enums.ConversationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code conversation} (V19).
 *
 * <p>{@code tripId} and {@code plannerSessionId} are raw {@code uuid} columns rather than
 * {@code @ManyToOne} associations, matching {@link TripEntity#getUserId()}. Reading a conversation
 * must not drag a trip — and through it a brief — into the persistence context: the chat history
 * endpoint wants messages, not the whole trip aggregate, and a lazy association here is how a
 * history read turns into three queries nothing at the call site asked for.
 *
 * <p>{@code nextMessageSeq} is the sequence allocator, advanced under {@code PESSIMISTIC_WRITE} by
 * {@code ConversationJpaRepository.findByIdAndUserIdForUpdate}. There is deliberately no
 * {@code @Version} on this entity: optimistic locking would turn two concurrent appends — both of
 * which are wanted — into a conflict, where the pessimistic lock simply queues the second. See
 * {@code Conversation}'s class comment and ADR 008 §1, which does not list this aggregate.
 */
@Entity
@Table(name = "conversation")
public class ConversationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "planner_session_id")
    private UUID plannerSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private ConversationScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private ConversationState state;

    @Column(name = "next_message_seq", nullable = false)
    private long nextMessageSeq;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mapper in the sibling package, which is why it is public. */
    public ConversationEntity() {
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

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public UUID getPlannerSessionId() {
        return plannerSessionId;
    }

    public void setPlannerSessionId(UUID plannerSessionId) {
        this.plannerSessionId = plannerSessionId;
    }

    public ConversationScope getScope() {
        return scope;
    }

    public void setScope(ConversationScope scope) {
        this.scope = scope;
    }

    public ConversationState getState() {
        return state;
    }

    public void setState(ConversationState state) {
        this.state = state;
    }

    public long getNextMessageSeq() {
        return nextMessageSeq;
    }

    public void setNextMessageSeq(long nextMessageSeq) {
        this.nextMessageSeq = nextMessageSeq;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
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
