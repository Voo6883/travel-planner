package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.ConversationEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code conversation} (V19).
 *
 * <p>Every finder carries {@code userId} (PLAN §4.0.2-L). {@code findByTripIdAndUserId} returns an
 * {@link Optional} rather than a list because {@code uq_conversation_trip_id} makes a second
 * conversation per trip impossible — PLAN §3.2's "one persistent conversation" expressed in the
 * return type, so no caller has to decide what to do with the second one.
 */
public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<ConversationEntity> findByTripIdAndUserId(UUID tripId, UUID userId);

    Optional<ConversationEntity> findByPlannerSessionIdAndUserId(UUID plannerSessionId, UUID userId);

    /** Newest first, matching {@code ix_conversation_user_created}. */
    List<ConversationEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Loads the conversation with a {@code SELECT … FOR UPDATE} row lock, so the caller can read
     * {@code next_message_seq}, advance it, and write it back with no window in between.
     *
     * <p>Pessimistic and not optimistic, deliberately. Two users' clients appending to the same
     * thread — or the user and the agent appending within one turn — both have work that must land;
     * a lock queues the second, while a {@code @Version} check would fail it and hand the caller a
     * conflict to retry for no reason. This is why {@code conversation} precedes {@code message} in
     * the lock order documented in {@code infrastructure/persistence/package-info.java}: a
     * transaction that took a message row first and then reached for its conversation would deadlock
     * against an ordinary append.
     *
     * <p>The lock is released with the caller's transaction. Adapters never open their own
     * (PLAN §4.0.2-H), which is what keeps the lock's lifetime the service method's, not a stream's
     * — tasks/20 forbids holding a transaction during a model call.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.id = :id and c.userId = :userId")
    Optional<ConversationEntity> findByIdAndUserIdForUpdate(
            @Param("id") UUID id, @Param("userId") UUID userId);
}
