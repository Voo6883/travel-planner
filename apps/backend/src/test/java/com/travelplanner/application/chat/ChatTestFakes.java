package com.travelplanner.application.chat;

import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Conversation;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.PlannerSession;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.port.ConversationRepositoryPort;
import com.travelplanner.domain.port.LlmPort;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;

/**
 * In-memory doubles for the chat suite.
 *
 * <p>Hand-written rather than mocked, for the reason the other {@code *TestFakes} in this project
 * give: the behaviour under test is a <em>sequence</em> of interactions — allocate, insert, re-read,
 * settle — and a mock asserts calls while a fake asserts outcomes. The idempotency test in
 * particular is only meaningful against something that enforces
 * {@code uq_message_conversation_client_id}, which {@link ConversationRepositoryFake} does.
 */
final class ChatTestFakes {

    private ChatTestFakes() {
    }

    static UserContext user(UUID userId) {
        return UserContext.of(userId, "traveller@example.com", Role.USER);
    }

    /**
     * The chat port, backed by maps.
     *
     * <p>Reproduces the three constraints the service actually depends on: {@code seq} is allocated
     * from the conversation and never reused, {@code (conversation_id, client_message_id)} is
     * unique, and every read is scoped by {@code user_id} so an unowned row is invisible rather than
     * forbidden.
     */
    static final class ConversationRepositoryFake implements ConversationRepositoryPort {

        private final Map<UUID, PlannerSession> sessions = new LinkedHashMap<>();
        private final Map<UUID, Conversation> conversations = new LinkedHashMap<>();
        private final Map<UUID, Message> messages = new LinkedHashMap<>();
        private final AtomicInteger saveMessageCalls = new AtomicInteger();

        int saveMessageCalls() {
            return saveMessageCalls.get();
        }

        Optional<Message> message(UUID messageId) {
            return Optional.ofNullable(messages.get(messageId));
        }

        List<Message> messagesOf(UUID conversationId) {
            return messages.values().stream()
                    .filter(message -> message.conversationId().equals(conversationId))
                    .sorted(Comparator.comparingLong(Message::seq))
                    .toList();
        }

        Optional<Conversation> conversation(UUID conversationId, UUID userId) {
            return findConversationByIdAndUserId(conversationId, userId);
        }

        Optional<PlannerSession> session(UUID sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public PlannerSession savePlannerSession(PlannerSession session) {
            sessions.put(session.id(), session);
            return session;
        }

        @Override
        public Optional<PlannerSession> findOpenPlannerSession(UUID userId) {
            return sessions.values().stream()
                    .filter(session -> session.isOwnedBy(userId) && session.isOpen())
                    .findFirst();
        }

        @Override
        public Conversation saveConversation(Conversation conversation) {
            conversations.put(conversation.id(), conversation);
            return conversation;
        }

        @Override
        public Optional<Conversation> findConversationByIdAndUserId(UUID conversationId, UUID userId) {
            return Optional.ofNullable(conversations.get(conversationId))
                    .filter(conversation -> conversation.isOwnedBy(userId));
        }

        @Override
        public Optional<Conversation> findConversationByTripIdAndUserId(UUID tripId, UUID userId) {
            return conversations.values().stream()
                    .filter(conversation -> conversation.isOwnedBy(userId))
                    .filter(conversation -> conversation.tripIfPresent().filter(tripId::equals).isPresent())
                    .findFirst();
        }

        @Override
        public Optional<Conversation> findConversationByPlannerSessionIdAndUserId(UUID sessionId, UUID userId) {
            return conversations.values().stream()
                    .filter(conversation -> conversation.isOwnedBy(userId))
                    .filter(conversation ->
                            conversation.plannerSessionIfPresent().filter(sessionId::equals).isPresent())
                    .findFirst();
        }

        @Override
        public List<Conversation> findConversationsByUserId(UUID userId, int pageNumber, int pageSize) {
            return conversations.values().stream()
                    .filter(conversation -> conversation.isOwnedBy(userId))
                    .skip((long) pageNumber * pageSize)
                    .limit(pageSize)
                    .toList();
        }

        @Override
        public long allocateSequence(UUID conversationId, UUID userId) {
            Conversation conversation = findConversationByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> ValidationFailedException.field("conversation_id",
                            "the conversation does not exist"));
            conversation.requireAppendable();
            long allocated = conversation.nextMessageSeq();
            conversations.put(conversationId, conversation.recordAppend(Instant.now()));
            return allocated;
        }

        @Override
        public Message appendMessage(Message message) {
            boolean duplicateClientId = message.clientMessageIdIfPresent()
                    .map(clientId -> findMessageByClientMessageId(
                            message.conversationId(), ownerOf(message), clientId).isPresent())
                    .orElse(false);
            if (duplicateClientId) {
                throw new IllegalStateException("uq_message_conversation_client_id");
            }
            boolean duplicateSeq = messages.values().stream()
                    .anyMatch(stored -> stored.conversationId().equals(message.conversationId())
                            && stored.seq() == message.seq());
            if (duplicateSeq) {
                throw new IllegalStateException("uq_message_conversation_seq");
            }
            messages.put(message.id(), message);
            return message;
        }

        @Override
        public Message saveMessage(Message message) {
            saveMessageCalls.incrementAndGet();
            messages.put(message.id(), message);
            return message;
        }

        @Override
        public Optional<Message> findMessageByClientMessageId(UUID conversationId, UUID userId,
                String clientMessageId) {
            if (clientMessageId == null || clientMessageId.isBlank()) {
                return Optional.empty();
            }
            return history(conversationId, userId).stream()
                    .filter(message -> message.hasClientMessageId(clientMessageId))
                    .findFirst();
        }

        @Override
        public List<Message> findMessagesAfter(UUID conversationId, UUID userId, long afterSeq, int limit) {
            return history(conversationId, userId).stream()
                    .filter(message -> message.seq() > afterSeq)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<Message> findLatestMessages(UUID conversationId, UUID userId, int limit) {
            List<Message> ordered = history(conversationId, userId);
            int from = Math.max(0, ordered.size() - limit);
            return List.copyOf(ordered.subList(from, ordered.size()));
        }

        @Override
        public long countMessages(UUID conversationId, UUID userId) {
            return history(conversationId, userId).size();
        }

        /** Oldest first, and empty for a conversation the user does not own. */
        private List<Message> history(UUID conversationId, UUID userId) {
            return findConversationByIdAndUserId(conversationId, userId)
                    .map(conversation -> messagesOf(conversationId))
                    .orElseGet(List::of);
        }

        private UUID ownerOf(Message message) {
            return conversations.get(message.conversationId()).userId();
        }
    }

    /** The trip port, holding only what chat asks of it: "is this trip the caller's?". */
    static final class TripRepositoryFake implements TripRepositoryPort {

        private final Map<UUID, Trip> trips = new LinkedHashMap<>();

        int count() {
            return trips.size();
        }

        Trip add(Trip trip) {
            trips.put(trip.id(), trip);
            return trip;
        }

        @Override
        public Trip save(Trip trip) {
            return add(trip);
        }

        @Override
        public Optional<Trip> findByIdAndUserId(UUID tripId, UUID userId) {
            return Optional.ofNullable(trips.get(tripId)).filter(trip -> trip.userId().equals(userId));
        }

        @Override
        public List<Trip> findAllByUserId(UUID userId) {
            return trips.values().stream().filter(trip -> trip.userId().equals(userId)).toList();
        }

        @Override
        public boolean deleteByIdAndUserId(UUID tripId, UUID userId) {
            return findByIdAndUserId(tripId, userId).map(trip -> trips.remove(tripId) != null).orElse(false);
        }
    }

    static final class TripBriefRepositoryFake implements TripBriefRepositoryPort {

        private final Map<UUID, TripBrief> briefs = new LinkedHashMap<>();

        @Override
        public TripBrief save(TripBrief brief) {
            briefs.put(brief.tripId(), brief);
            return brief;
        }

        @Override
        public Optional<TripBrief> findByTripId(UUID tripId) {
            return Optional.ofNullable(briefs.get(tripId));
        }
    }

    /**
     * A scriptable {@link LlmPort}.
     *
     * <p>The stream is supplied per test as a {@code Supplier<Flux<LlmEvent>>} so a test can hand
     * over a flux that never completes — which is how disconnect and heartbeat behaviour is
     * exercised without a real provider or a real socket.
     */
    static final class ScriptedLlm implements LlmPort {

        private final Supplier<Flux<LlmEvent>> script;
        private final List<Prompt> prompts = new ArrayList<>();
        private final List<List<ToolSpec>> offeredTools = new ArrayList<>();

        ScriptedLlm(Supplier<Flux<LlmEvent>> script) {
            this.script = script;
        }

        static ScriptedLlm emitting(LlmEvent... events) {
            return new ScriptedLlm(() -> Flux.fromArray(events));
        }

        List<Prompt> prompts() {
            return List.copyOf(prompts);
        }

        List<List<ToolSpec>> offeredTools() {
            return List.copyOf(offeredTools);
        }

        @Override
        public String providerName() {
            return "scripted";
        }

        @Override
        public String complete(Prompt prompt, LlmOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T completeStructured(Prompt prompt, Class<T> type, LlmOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
            return stream(prompt, List.of(), options);
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
            prompts.add(prompt);
            offeredTools.add(List.copyOf(tools));
            return Flux.defer(script::get);
        }
    }
}
