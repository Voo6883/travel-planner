package com.travelplanner.domain.port;

import com.travelplanner.domain.model.Conversation;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.PlannerSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the chat aggregates — {@link PlannerSession}, {@link Conversation},
 * {@link Message} (PLAN §3.2, §8; tasks/20). Implemented in {@code infrastructure/persistence/}.
 *
 * <p><strong>Every read takes the owner's id.</strong> There is no {@code findConversationById} and
 * no {@code findMessages(conversationId)} without a user, and the omission is the point: PLAN
 * §4.0.2-L requires every query to filter by the {@code user_id} from
 * {@link com.travelplanner.domain.valueobject.UserContext}, and a port offering an unscoped lookup
 * makes reading somebody else's chat a one-line mistake. A missing row and another user's row are
 * deliberately indistinguishable — both {@link Optional#empty()} — so a 404 cannot be used to probe
 * for the existence of a conversation. {@code message} carries no {@code user_id} column of its
 * own; the adapter joins {@code conversation} for the scoping predicate rather than denormalising
 * an owner that could disagree with the one on the thread.
 *
 * <p><strong>Three tables, one port.</strong> They are one consistency boundary, not three: a
 * message cannot exist without a sequence number allocated from its conversation, and the handoff
 * closes a session and links a conversation in the same transaction. Splitting them across three
 * ports would let a caller take those steps separately, which is exactly the ordering hazard the
 * lock order in {@code infrastructure/persistence/package-info.java} exists to prevent.
 *
 * <h2>How an append works, and why it is three calls</h2>
 *
 * <pre>
 *   long seq = allocateSequence(conversationId, userId);   // locks the conversation row
 *   Message stored = appendMessage(Message.fromUser(conversationId, seq, text, clientId, now));
 *   saveConversation(conversation.recordAppend(now));      // advances lastMessageAt
 * </pre>
 *
 * <p>Splitting allocation from insertion is what lets the domain build a fully-formed, validated
 * {@link Message} — a record cannot be constructed without its {@code seq}, and a port method that
 * took an unsequenced message would have to construct one itself, inside infrastructure, where the
 * invariants do not live.
 *
 * <h2>Idempotency (tasks/20 Definition of Done)</h2>
 *
 * <p>"Disconnect/retry cannot duplicate committed user messages." The service resolves a retry by
 * calling {@link #findMessageByClientMessageId} first and returning the existing message when it
 * hits. That check alone is not enough — two retries racing after a dropped response would both
 * pass it — so {@code uq_message_conversation_client_id} is the real guarantee:
 * {@link #appendMessage} propagates the constraint violation, the loser's transaction rolls back,
 * and the retry that follows finds the committed row. Look-then-insert makes the common case cheap;
 * the unique index makes the rare case correct.
 */
public interface ConversationRepositoryPort {

    // ------------------------------------------------------------------------------------------
    // Planner sessions — the pre-trip chat context.
    // ------------------------------------------------------------------------------------------

    /** Inserts or updates. */
    PlannerSession savePlannerSession(PlannerSession session);

    /**
     * The user's resumable planner chat, if one is open.
     *
     * <p>Backed by the partial unique index {@code uq_planner_session_user_open}, which is also why
     * this returns at most one: "which of my three planner sessions is current?" is a question the
     * schema refuses to allow to exist.
     */
    Optional<PlannerSession> findOpenPlannerSession(UUID userId);

    // ------------------------------------------------------------------------------------------
    // Conversations.
    // ------------------------------------------------------------------------------------------

    /** Inserts or updates. */
    Conversation saveConversation(Conversation conversation);

    Optional<Conversation> findConversationByIdAndUserId(UUID conversationId, UUID userId);

    /**
     * The trip's one persistent conversation (PLAN §3.2). At most one exists —
     * {@code uq_conversation_trip_id}.
     */
    Optional<Conversation> findConversationByTripIdAndUserId(UUID tripId, UUID userId);

    /** The conversation a planner session is carrying, before any {@code create_trip} handoff. */
    Optional<Conversation> findConversationByPlannerSessionIdAndUserId(UUID sessionId, UUID userId);

    /**
     * The user's conversations, newest first — the order {@code ix_conversation_user_created}
     * serves.
     *
     * <p>Paged with primitive {@code int}s rather than with {@code application/page/PageQuery}:
     * PLAN §4.0.1 forbids the domain from importing outward, and {@code PageQuery} lives in the
     * application layer.
     *
     * <p>Offset paging here, cursor paging for messages ({@link #findMessagesAfter}) — the
     * difference is not an inconsistency. A conversation list is short, browsed by page number, and
     * tolerates a row shifting between requests. A message history is long, is read strictly in
     * order, and must never skip or repeat a turn when one is appended mid-scroll, which offset
     * paging cannot promise and a {@code seq} cursor gets for free.
     *
     * @param pageNumber zero-based
     * @param pageSize at least 1
     */
    List<Conversation> findConversationsByUserId(UUID userId, int pageNumber, int pageSize);

    // ------------------------------------------------------------------------------------------
    // Messages.
    // ------------------------------------------------------------------------------------------

    /**
     * Reserves the next sequence number for a conversation, advancing the stored counter.
     *
     * <p>Takes a pessimistic row lock on {@code conversation} — {@code SELECT … FOR UPDATE} — so
     * two concurrent appends queue rather than both reading the same number and one of them failing
     * {@code uq_message_conversation_seq}. This is why {@code conversation} precedes {@code message}
     * in the documented lock order.
     *
     * @throws com.travelplanner.domain.exception.ValidationFailedException when the conversation is
     *         archived, or does not exist for this user — a read-only thread must refuse the number
     *         rather than the insert, so nothing has been written when the refusal happens
     */
    long allocateSequence(UUID conversationId, UUID userId);

    /**
     * Inserts a new message.
     *
     * <p>Insert, never update — see {@link #saveMessage} for the streaming case. A duplicate
     * {@code clientMessageId} or a duplicate {@code seq} surfaces as the underlying constraint
     * violation rather than being swallowed: both mean a caller raced, and silently returning the
     * existing row would hide a sequence-allocation bug behind an idempotency feature.
     */
    Message appendMessage(Message message);

    /**
     * Updates an existing message — the incremental persistence ADR 007 requires ("Assistant
     * messages are persisted incrementally so resume/reload is served from the DB, not from
     * memory"), and the write that marks a partial message {@code INTERRUPTED} on disconnect.
     */
    Message saveMessage(Message message);

    /**
     * The idempotency lookup. Present when this exact send already committed, in which case the
     * caller returns it instead of appending a second copy.
     */
    Optional<Message> findMessageByClientMessageId(UUID conversationId, UUID userId,
            String clientMessageId);

    /**
     * History in order, oldest first, starting after {@code afterSeq}.
     *
     * <p>The one read that serves both product needs: paging forward through a long thread, and
     * ADR 007 resume, where {@code afterSeq} is the client's {@code Last-Event-ID}. Pass
     * {@code afterSeq = 0} for the beginning — {@link Conversation#FIRST_SEQ} is 1, so 0 is before
     * everything and needs no separate overload. Backed by
     * {@code uq_message_conversation_seq}, which is why the ordering costs no sort.
     */
    List<Message> findMessagesAfter(UUID conversationId, UUID userId, long afterSeq, int limit);

    /**
     * The newest {@code limit} messages, returned oldest first.
     *
     * <p>What a chat panel opens with, and what context assembly windows on: a conversation is
     * read from the end, but must be rendered — and sent to the model — from the start. Doing the
     * reversal here keeps every caller from having to remember to.
     */
    List<Message> findLatestMessages(UUID conversationId, UUID userId, int limit);

    /** Total messages in a thread, for "load older" affordances and for truncation decisions. */
    long countMessages(UUID conversationId, UUID userId);
}
