package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.MessageEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code message} (V19).
 *
 * <p><strong>Why every query joins {@code ConversationEntity}.</strong> {@code message} has no
 * {@code user_id} column: the owner lives on the conversation, and copying it onto every message
 * would create a second source of truth that a mis-set {@code conversation_id} could put in
 * disagreement with the first. PLAN §4.0.2-L still requires the owner predicate on every read, so
 * each query below states it explicitly — {@code and c.userId = :userId} is visible in the query
 * text, where review can see it, rather than hidden behind an entity association that looks like a
 * free property access.
 *
 * <p>The join costs one primary-key lookup. There is no derived finder on this interface at all,
 * for exactly that reason: Spring Data would happily derive {@code findByConversationId} — an
 * unscoped read of somebody else's chat — from a name three characters shorter than the safe one.
 *
 * <p>Ordering is always {@code seq}, never {@code created_at}. Postgres fixes {@code now()} for a
 * whole transaction, so every row a single turn writes carries an identical timestamp and ordering
 * on it is undefined rather than merely imprecise. {@code uq_message_conversation_seq} serves both
 * directions, so neither ordering costs a sort.
 */
public interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    /**
     * History in order, starting after {@code afterSeq}. Also the ADR 007 resume read, where
     * {@code afterSeq} is the client's {@code Last-Event-ID}; pass 0 to start from the beginning,
     * since sequence numbers begin at 1.
     */
    @Query("""
            select m from MessageEntity m, ConversationEntity c
            where c.id = m.conversationId
              and m.conversationId = :conversationId
              and c.userId = :userId
              and m.seq > :afterSeq
            order by m.seq asc
            """)
    List<MessageEntity> findHistoryAfter(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            @Param("afterSeq") long afterSeq,
            Pageable pageable);

    /**
     * The newest messages first — what a chat panel opens with. The adapter reverses the result,
     * because a thread is read from the end but must be rendered, and sent to a model, from the
     * start.
     */
    @Query("""
            select m from MessageEntity m, ConversationEntity c
            where c.id = m.conversationId
              and m.conversationId = :conversationId
              and c.userId = :userId
            order by m.seq desc
            """)
    List<MessageEntity> findLatest(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            Pageable pageable);

    /**
     * The idempotency lookup (tasks/20 Definition of Done). Returns at most one row because
     * {@code uq_message_conversation_client_id} makes a second impossible — which is also why the
     * caller may treat a hit as "this exact send already committed" rather than as "one of the
     * sends with this id".
     */
    @Query("""
            select m from MessageEntity m, ConversationEntity c
            where c.id = m.conversationId
              and m.conversationId = :conversationId
              and c.userId = :userId
              and m.clientMessageId = :clientMessageId
            """)
    Optional<MessageEntity> findByClientMessageId(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            @Param("clientMessageId") String clientMessageId);

    @Query("""
            select count(m) from MessageEntity m, ConversationEntity c
            where c.id = m.conversationId
              and m.conversationId = :conversationId
              and c.userId = :userId
            """)
    long countHistory(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId);
}
