package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Conversation;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.PlannerSession;
import com.travelplanner.domain.port.ConversationRepositoryPort;
import com.travelplanner.infrastructure.persistence.entity.ConversationEntity;
import com.travelplanner.infrastructure.persistence.entity.MessageEntity;
import com.travelplanner.infrastructure.persistence.entity.PlannerSessionEntity;
import com.travelplanner.infrastructure.persistence.mapper.ConversationPersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.MessagePersistenceMapper;
import com.travelplanner.infrastructure.persistence.mapper.PlannerSessionPersistenceMapper;
import com.travelplanner.infrastructure.persistence.repository.ConversationJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.MessageJpaRepository;
import com.travelplanner.infrastructure.persistence.repository.PlannerSessionJpaRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * {@link ConversationRepositoryPort} over JPA. The only place the three chat entities are
 * constructed.
 *
 * <p>No {@code @Transactional} here. Adapters join the service's transaction (PLAN §4.0.2-H) —
 * which matters more than usual for this port: {@link #allocateSequence} takes a row lock, and a
 * self-declared transaction would end it the moment the method returned, leaving the append it was
 * protecting unguarded.
 *
 * <p>Every write goes through a <em>detached</em> entity built from the domain record, the same
 * shape as {@link TripRepositoryAdapter}. Here the reason is not optimistic locking — none of these
 * aggregates has a version — but the mapper contract: {@code applyToEntity} copies every field, so
 * building the entity fresh guarantees the row reflects exactly the domain object handed in, with
 * no residue from a previous load.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class ConversationRepositoryAdapter implements ConversationRepositoryPort {

    private final PlannerSessionJpaRepository plannerSessions;
    private final ConversationJpaRepository conversations;
    private final MessageJpaRepository messages;
    private final PlannerSessionPersistenceMapper plannerSessionMapper;
    private final ConversationPersistenceMapper conversationMapper;
    private final MessagePersistenceMapper messageMapper;

    public ConversationRepositoryAdapter(
            PlannerSessionJpaRepository plannerSessions,
            ConversationJpaRepository conversations,
            MessageJpaRepository messages,
            PlannerSessionPersistenceMapper plannerSessionMapper,
            ConversationPersistenceMapper conversationMapper,
            MessagePersistenceMapper messageMapper) {
        this.plannerSessions = plannerSessions;
        this.conversations = conversations;
        this.messages = messages;
        this.plannerSessionMapper = plannerSessionMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public PlannerSession savePlannerSession(PlannerSession session) {
        PlannerSessionEntity entity = new PlannerSessionEntity();
        plannerSessionMapper.applyToEntity(session, entity);
        return plannerSessionMapper.toDomain(plannerSessions.save(entity));
    }

    @Override
    public Optional<PlannerSession> findOpenPlannerSession(UUID userId) {
        return plannerSessions.findByUserIdAndEndedAtIsNull(userId).map(plannerSessionMapper::toDomain);
    }

    @Override
    public Conversation saveConversation(Conversation conversation) {
        ConversationEntity entity = new ConversationEntity();
        conversationMapper.applyToEntity(conversation, entity);
        return conversationMapper.toDomain(conversations.save(entity));
    }

    @Override
    public Optional<Conversation> findConversationByIdAndUserId(UUID conversationId, UUID userId) {
        return conversations.findByIdAndUserId(conversationId, userId).map(conversationMapper::toDomain);
    }

    @Override
    public Optional<Conversation> findConversationByTripIdAndUserId(UUID tripId, UUID userId) {
        return conversations.findByTripIdAndUserId(tripId, userId).map(conversationMapper::toDomain);
    }

    @Override
    public Optional<Conversation> findConversationByPlannerSessionIdAndUserId(UUID sessionId, UUID userId) {
        return conversations.findByPlannerSessionIdAndUserId(sessionId, userId)
                .map(conversationMapper::toDomain);
    }

    @Override
    public List<Conversation> findConversationsByUserId(UUID userId, int pageNumber, int pageSize) {
        return conversations.findAllByUserIdOrderByCreatedAtDesc(userId, page(pageNumber, pageSize))
                .stream()
                .map(conversationMapper::toDomain)
                .toList();
    }

    @Override
    public long allocateSequence(UUID conversationId, UUID userId) {
        // FOR UPDATE. Between reading next_message_seq and writing it back there must be no window
        // in which a second appender can read the same value — that is the whole job of this method,
        // and an ordinary findByIdAndUserId would leave exactly such a window.
        ConversationEntity entity = conversations.findByIdAndUserIdForUpdate(conversationId, userId)
                .orElseThrow(() -> ValidationFailedException.field("conversation_id",
                        "the conversation does not exist"));
        // Reuses the domain rule rather than re-testing `state == ARCHIVED` here: a read-only
        // thread must refuse the NUMBER, so that a refusal leaves nothing written and no gap in the
        // sequence. Mapping first also means the archived check is the domain's, in one place.
        conversationMapper.toDomain(entity).requireAppendable();

        long allocated = entity.getNextMessageSeq();
        entity.setNextMessageSeq(allocated + 1);
        // saveAndFlush, not save: the UPDATE must reach the database before the caller inserts the
        // message that depends on it, or a deferred flush would reorder the two writes and the row
        // lock would be released later than the insert it is protecting.
        conversations.saveAndFlush(entity);
        return allocated;
    }

    @Override
    public Message appendMessage(Message message) {
        return saveMessage(message);
    }

    @Override
    public Message saveMessage(Message message) {
        MessageEntity entity = new MessageEntity();
        messageMapper.applyToEntity(message, entity);
        // saveAndFlush so a duplicate client_message_id or seq surfaces here, at the call that
        // caused it, rather than at commit time where the stack trace names the transaction manager
        // and not the append. The idempotency guarantee depends on that violation being visible.
        return messageMapper.toDomain(messages.saveAndFlush(entity));
    }

    @Override
    public Optional<Message> findMessageByClientMessageId(UUID conversationId, UUID userId,
            String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            // A blank key matches nothing by definition; issuing the query would risk matching a
            // row whose client_message_id happened to be blank, which the domain forbids anyway.
            return Optional.empty();
        }
        return messages.findByClientMessageId(conversationId, userId, clientMessageId)
                .map(messageMapper::toDomain);
    }

    @Override
    public List<Message> findMessagesAfter(UUID conversationId, UUID userId, long afterSeq, int limit) {
        return messages.findHistoryAfter(conversationId, userId, afterSeq, page(0, limit)).stream()
                .map(messageMapper::toDomain)
                .toList();
    }

    @Override
    public List<Message> findLatestMessages(UUID conversationId, UUID userId, int limit) {
        List<Message> newestFirst = new ArrayList<>(
                messages.findLatest(conversationId, userId, page(0, limit)).stream()
                        .map(messageMapper::toDomain)
                        .toList());
        // Read from the end, rendered from the start. Reversing here rather than in each caller is
        // the difference between one place that can get it wrong and every place.
        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    @Override
    public long countMessages(UUID conversationId, UUID userId) {
        return messages.countHistory(conversationId, userId);
    }

    /**
     * Translates the port's primitive paging into Spring Data's. The domain may not import
     * {@code Pageable} or {@code application/page/PageQuery} (PLAN §4.0.1), so the conversion
     * belongs on this side of the boundary.
     *
     * <p>The message reads pass {@code pageNumber = 0} because their window is chosen by the
     * {@code seq} cursor in the query, not by an offset — {@code Pageable} is doing nothing there
     * but supplying a {@code LIMIT}.
     */
    private static Pageable page(int pageNumber, int pageSize) {
        if (pageSize < 1) {
            throw ValidationFailedException.field("page_size", "must be at least 1");
        }
        if (pageNumber < 0) {
            throw ValidationFailedException.field("page_number", "must not be negative");
        }
        return PageRequest.of(pageNumber, pageSize);
    }
}
