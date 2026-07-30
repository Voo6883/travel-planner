package com.travelplanner.application.chat;

import com.travelplanner.application.page.PageQuery;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.ChatMessageStatus;
import com.travelplanner.domain.exception.ConversationNotFoundException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.model.Conversation;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.PlannerSession;
import com.travelplanner.domain.port.ConversationRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every database interaction a chat turn needs, and <strong>nothing else</strong>.
 *
 * <h2>This class exists to keep transactions off the streaming path</h2>
 *
 * <p>tasks/20 "Do not": <em>do not hold database transactions during streaming/model calls</em>. A
 * transaction that spanned a turn would pin one pooled connection for the whole length of a model
 * response — seconds, or minutes for a long answer — so a handful of concurrent chats would exhaust
 * the pool and every unrelated request in the application would start timing out. It is the kind of
 * fault that only appears under load and is then extremely hard to attribute.
 *
 * <p>The separation is therefore structural rather than remembered: every {@code @Transactional}
 * method in the chat feature is on this class, {@link ChatTurnService} has none, and
 * {@link ChatTurnService} owns the {@code Flux}. A turn is three or four short transactions with
 * the stream strictly between them:
 *
 * <pre>
 *   tx1  resolveForAppend   — find or open the conversation
 *   tx2  appendUserMessage  — commit the user's words, idempotently
 *   tx3  openAssistantMessage — persist the empty STREAMING row
 *   ---- no transaction, no connection held: the model call and the SSE stream ----
 *   tx4  settleAssistantMessage — COMPLETE, INTERRUPTED, or FAILED
 * </pre>
 *
 * <p>Splitting {@code tx2} from {@code tx3} rather than doing both in one is deliberate: {@code tx2}
 * can fail on the idempotency constraint and be resolved by re-reading, while {@code tx3} allocates
 * a second sequence number. Sharing a transaction would roll the committed user message back
 * because the assistant row could not be opened, which is exactly the "disconnect/retry duplicates
 * a message" failure the client id exists to prevent.
 */
@Service
@RequiresDatabase
public class ChatConversationService {

    /**
     * How many stored messages are handed to the model as context.
     *
     * <p>A cap rather than a token budget, for the reason {@code ConversationContextPort} gives:
     * tokenisation is provider-specific and lives behind {@code LlmPort}. Tasks 21/22 replace this
     * with a real {@code ConversationContextPort} implementation; until they do, a fixed window
     * keeps the prompt bounded without pretending to be smarter than it is.
     */
    static final int CONTEXT_WINDOW = 20;

    private final ConversationRepositoryPort conversations;
    private final TripRepositoryPort trips;

    public ChatConversationService(ConversationRepositoryPort conversations, TripRepositoryPort trips) {
        this.conversations = conversations;
        this.trips = trips;
    }

    /**
     * The conversation a send belongs to, opening one when the surface has none yet.
     *
     * <p>A first planner turn creates a {@code planner_session} and its conversation here rather
     * than lazily during the stream, so that by the time any token is generated there is a durable
     * row for it to be attributed to.
     *
     * @throws ConversationNotFoundException when {@code conversationId} is not the caller's, or
     *         does not belong to {@code target} — a trip thread addressed through the planner path
     *         is as wrong as one owned by somebody else, and both answer 404
     * @throws com.travelplanner.domain.exception.ValidationFailedException when the thread is
     *         archived. Refused before the model call, not after: discovering a thread is read-only
     *         once the tokens are paid for is too late
     */
    @TransactionalWrite
    public Conversation resolveForAppend(ChatTarget target, UUID conversationId, UserContext user) {
        Conversation conversation = conversationId == null
                ? openFor(target, user)
                : requireOwned(conversationId, user);
        requireMatchesTarget(conversation, target);
        conversation.requireAppendable();
        return conversation;
    }

    /**
     * Commits the user's words, or returns the copy a previous attempt already committed.
     *
     * <p><strong>This is the idempotency guarantee</strong> (tasks/20 DoD: "disconnect/retry cannot
     * duplicate committed user messages"). The lookup makes the common retry cheap; the unique index
     * {@code uq_message_conversation_client_id} is what makes the racing case correct, because
     * look-then-insert is not atomic and two retries arriving together would both pass the lookup.
     * The loser's transaction rolls back on the constraint and its retry finds the committed row.
     *
     * <p>Note what is <em>not</em> done on the duplicate path: no sequence is allocated. Allocating
     * one and then discarding it would leave a permanent hole in the conversation's ordering for
     * every retry a flaky connection produced.
     */
    @TransactionalWrite
    public Message appendUserMessage(UUID conversationId, String clientMessageId, String content,
            UserContext user) {
        Optional<Message> committed =
                conversations.findMessageByClientMessageId(conversationId, user.userId(), clientMessageId);
        if (committed.isPresent()) {
            return committed.get();
        }
        Instant now = Instant.now();
        long seq = conversations.allocateSequence(conversationId, user.userId());
        Message stored = conversations.appendMessage(
                Message.fromUser(conversationId, seq, content, clientMessageId, now));
        touch(conversationId, user, now);
        return stored;
    }

    /**
     * Opens the empty {@code STREAMING} assistant row, before the first token.
     *
     * <p>ADR 007: "assistant messages are persisted incrementally so resume/reload is served from
     * the DB, not from memory". The row existing up front is also what gives a disconnect something
     * to mark — without it, a connection dropped mid-answer would have nothing to set
     * {@code INTERRUPTED} on, and the partial answer really would be silently discarded.
     */
    @TransactionalWrite
    public Message openAssistantMessage(UUID conversationId, UserContext user) {
        Instant now = Instant.now();
        long seq = conversations.allocateSequence(conversationId, user.userId());
        Message opened = conversations.appendMessage(Message.assistantStreamStarted(conversationId, seq, now));
        touch(conversationId, user, now);
        return opened;
    }

    /**
     * Writes the assistant turn's text and its terminal status — the last of the four transactions,
     * and the one that runs after the stream has finished.
     *
     * <p>Called for every ending, including the ones nobody wants: {@code INTERRUPTED} for a client
     * that walked away mid-answer, {@code FAILED} for a provider that stopped. Whatever text arrived
     * is kept in both cases (ADR 007: "never silently discarded"), because the common mobile failure
     * is a transient network loss part-way through a long answer, and throwing away what the user
     * already watched arrive is worse than labelling it as cut short.
     *
     * @param streaming the row {@link #openAssistantMessage} returned, still {@code STREAMING}
     * @param text everything the model emitted before the ending, possibly empty
     */
    @TransactionalWrite
    public Message settleAssistantMessage(Message streaming, ChatMessageStatus terminal, String text) {
        Instant now = Instant.now();
        Message withText = streaming.appendContent(text, now);
        Message settled = switch (terminal) {
            case COMPLETE -> withText.complete(now);
            case INTERRUPTED -> withText.interrupt(now);
            case FAILED -> withText.fail(now);
            case STREAMING -> throw new IllegalArgumentException("STREAMING is not a terminal status");
        };
        return conversations.saveMessage(settled);
    }

    /**
     * What a reconnecting client is missing, or empty when there is nothing to resume (ADR 007; F-39).
     *
     * <p>A resume is answerable only when the turn the client was watching has <strong>already
     * finished</strong>. That is the case worth handling, and it is common: a socket dies after the
     * model completed but before the browser processed {@code done}, and every byte of the answer is
     * sitting in {@code message}. Today that reconnect regenerates the whole turn — a second provider
     * call, billed, producing a <em>different</em> answer from the one the user had started reading,
     * and leaving two assistant messages in history for one question.
     *
     * <p>When the turn is still {@code STREAMING} or was left {@code INTERRUPTED} there is nothing
     * honest to replay: token deltas are not persisted individually, so the server cannot hand back
     * the half-sentence the client already has. Those reconnects still regenerate, which is why
     * ADR 007's wording had to change rather than be implemented as written — see the ADR's resume
     * row and {@code ChatTurnService.replay}.
     *
     * @param afterSeq the client's {@code Last-Event-ID}. Frames at or below it are already applied
     * @return every message after {@code afterSeq}, oldest first
     */
    @Transactional(readOnly = true)
    public List<Message> messagesAfter(UUID conversationId, long afterSeq, UserContext user) {
        return conversations.findMessagesAfter(conversationId, user.userId(), afterSeq, CONTEXT_WINDOW);
    }

    /**
     * The message a previous attempt committed under this {@code clientMessageId}, if any.
     *
     * <p>Exposed separately from {@link #appendUserMessage} because the resume path has to ask the
     * question <em>without</em> the side effect: appending is what makes a fresh send a turn, and a
     * reconnect must be able to discover "you already sent this" before anything is written.
     */
    @Transactional(readOnly = true)
    public Optional<Message> findCommittedUserMessage(UUID conversationId, String clientMessageId,
            UserContext user) {
        return conversations.findMessageByClientMessageId(conversationId, user.userId(), clientMessageId);
    }

    /**
     * The conversation this target already has, without opening one.
     *
     * <p>{@link #resolveForAppend} would create a planner session for a caller who has none, which is
     * right for a send and wrong for a resume: a reconnect that creates a conversation has resumed
     * nothing and has made a row.
     */
    @Transactional(readOnly = true)
    public Optional<Conversation> findExisting(ChatTarget target, UUID conversationId, UserContext user) {
        return locate(target, conversationId, user);
    }

    /** The model's view of the thread so far, oldest first and bounded by {@link #CONTEXT_WINDOW}. */
    @Transactional(readOnly = true)
    public List<Message> contextWindow(UUID conversationId, UserContext user) {
        return conversations.findLatestMessages(conversationId, user.userId(), CONTEXT_WINDOW);
    }

    /**
     * One page of history, newest page first, {@code seq} ascending within the page.
     *
     * <p>See {@link ChatHistoryPage} for why page 0 is the newest page rather than the oldest.
     *
     * <p><strong>Known cost.</strong> The window is taken with {@code findLatestMessages}, which
     * reads {@code (page + 1) × page_size} rows and keeps the first {@code page_size} of them. The
     * port publishes no offset-from-the-end read — it offers a {@code seq} cursor, which cannot be
     * used here because {@code seq} may contain gaps (a rolled-back transaction consumes a number)
     * and an offset derived from it would skip or repeat rows. Deep paging into a very long thread
     * is therefore linear rather than constant; a cursor-paged history endpoint is the fix, and it
     * belongs with the port change rather than with this slice, which has no way to test an adapter
     * without Docker.
     *
     * @return an empty page, not a 404, when the caller has no conversation on this surface yet —
     *         a new account opening the planner home has no history and that is an answer
     */
    @Transactional(readOnly = true)
    public ChatHistoryPage history(ChatTarget target, UUID conversationId, PageQuery query,
            UserContext user) {
        Optional<Conversation> found = locate(target, conversationId, user);
        if (found.isEmpty()) {
            return ChatHistoryPage.empty(query.page(), query.pageSize());
        }
        Conversation conversation = found.get();
        long total = conversations.countMessages(conversation.id(), user.userId());
        long skipped = (long) query.page() * query.pageSize();
        if (skipped >= total) {
            return new ChatHistoryPage(query.page(), query.pageSize(), total, conversation.id(), List.of());
        }
        int window = (int) Math.min(skipped + query.pageSize(), total);
        List<Message> newestWindow =
                conversations.findLatestMessages(conversation.id(), user.userId(), window);
        // `newestWindow` is the newest `window` messages, oldest first. The requested page is its
        // leading slice: everything after the first `size` entries belongs to a newer page the
        // caller already has.
        int end = Math.max(0, newestWindow.size() - (int) skipped);
        return new ChatHistoryPage(query.page(), query.pageSize(), total, conversation.id(),
                newestWindow.subList(0, Math.min(end, newestWindow.size())));
    }

    // ------------------------------------------------------------------------------------------
    // Resolution helpers. Every one of them is user-scoped; there is no unscoped path in or out.
    // ------------------------------------------------------------------------------------------

    private Conversation openFor(ChatTarget target, UserContext user) {
        return target.isPlanner() ? openPlannerConversation(user) : openTripConversation(target.tripId(), user);
    }

    /**
     * The caller's resumable planner thread, opening a session and a conversation if there is none.
     *
     * <p>At most one session can be open per user — {@code uq_planner_session_user_open} — so two
     * tabs racing here produce one thread rather than splitting the user's chat in half.
     */
    private Conversation openPlannerConversation(UserContext user) {
        Instant now = Instant.now();
        PlannerSession session = conversations.findOpenPlannerSession(user.userId())
                .orElseGet(() -> conversations.savePlannerSession(PlannerSession.open(user.userId(), now)));
        return conversations.findConversationByPlannerSessionIdAndUserId(session.id(), user.userId())
                .orElseGet(() -> conversations.saveConversation(
                        Conversation.startPlanner(user.userId(), session.id(), now)));
    }

    /**
     * The trip's one conversation, created on first use.
     *
     * <p>The trip is looked up before the conversation is created, and only through the user-scoped
     * lookup: without that check, posting to {@code /trips/{someone-elses-id}/chat/messages} would
     * happily create a conversation row pointing at another user's trip.
     */
    private Conversation openTripConversation(UUID tripId, UserContext user) {
        return conversations.findConversationByTripIdAndUserId(tripId, user.userId())
                .orElseGet(() -> {
                    trips.findByIdAndUserId(tripId, user.userId()).orElseThrow(TripNotFoundException::new);
                    return conversations.saveConversation(
                            Conversation.startForTrip(user.userId(), tripId, Instant.now()));
                });
    }

    /** The read path's counterpart to {@link #openFor}: finds, never creates. */
    private Optional<Conversation> locate(ChatTarget target, UUID conversationId, UserContext user) {
        if (conversationId != null) {
            Conversation conversation = requireOwned(conversationId, user);
            requireMatchesTarget(conversation, target);
            return Optional.of(conversation);
        }
        if (!target.isPlanner()) {
            // Ownership is proved even when no conversation exists, so that a trip belonging to
            // somebody else answers 404 rather than "an empty history", which would confirm the id.
            trips.findByIdAndUserId(target.tripId(), user.userId()).orElseThrow(TripNotFoundException::new);
            return conversations.findConversationByTripIdAndUserId(target.tripId(), user.userId());
        }
        return conversations.findOpenPlannerSession(user.userId())
                .flatMap(session -> conversations.findConversationByPlannerSessionIdAndUserId(
                        session.id(), user.userId()));
    }

    private Conversation requireOwned(UUID conversationId, UserContext user) {
        return conversations.findConversationByIdAndUserId(conversationId, user.userId())
                .orElseThrow(ConversationNotFoundException::new);
    }

    /**
     * A conversation reached through the wrong path is a 404, not a redirect.
     *
     * <p>The planner path and a trip path are different resources. Letting a trip conversation be
     * driven through {@code /planner/chat/messages} would mean the surface a client is on no longer
     * predicts which thread it is writing to.
     */
    private static void requireMatchesTarget(Conversation conversation, ChatTarget target) {
        boolean matches = target.isPlanner()
                ? conversation.tripIfPresent().isEmpty()
                : conversation.tripIfPresent().filter(target.tripId()::equals).isPresent();
        if (!matches) {
            throw new ConversationNotFoundException();
        }
    }

    /**
     * Advances {@code last_message_at} after an append.
     *
     * <p>Re-read rather than derived from the caller's copy, and deliberately not
     * {@link Conversation#recordAppend(Instant)}: {@code allocateSequence} has already advanced
     * {@code next_message_seq} in the row, so writing back a value computed from a snapshot taken
     * before the allocation would undo it. The re-read happens while this transaction still holds
     * the {@code FOR UPDATE} lock the allocation took, so nothing can move underneath it.
     */
    private void touch(UUID conversationId, UserContext user, Instant now) {
        conversations.findConversationByIdAndUserId(conversationId, user.userId())
                .ifPresent(conversation -> conversations.saveConversation(conversation.touchLastMessageAt(now)));
    }
}
