package com.travelplanner.application.tripchat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.application.ai.LlmStreamPort;
import com.travelplanner.application.chat.ChatTarget;
import com.travelplanner.application.chat.ChatTurn;
import com.travelplanner.application.knowledge.SupportedDestinationService;
import com.travelplanner.application.trip.TripAccess;
import com.travelplanner.application.trip.TripBriefService;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.Conversation;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.PlannerSession;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.port.ConversationRepositoryPort;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import com.travelplanner.domain.valueobject.UserContext;
import com.travelplanner.domain.exception.VersionConflictException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * The trip-intake orchestrator: which tools it offers per status, the status gate and validation
 * refusals it turns into typed errors without mutating state, and the successful write that emits a
 * tool result and a {@code brief_updated} domain event (task 22, UC-C5-01/02/09).
 *
 * <p>Asserted on the {@link LlmEvent} stream the orchestrator produces rather than through
 * {@code ChatTurnService} — the mapping to wire frames has its own suite, and the properties worth
 * pinning here are "what did the orchestrator decide" and "what did the deterministic services do".
 */
class TripChatOrchestratorTest {

    private static final DateRange SPRING =
            DateRange.of(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 12));
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    private final InMemoryTrips trips = new InMemoryTrips();
    private final InMemoryBriefs briefs = new InMemoryBriefs();
    private final ConversationFake conversations = new ConversationFake();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserContext caller;
    private UUID tripId;
    private UUID conversationId;
    private TripBriefService briefService;
    private TripChatToolService toolService;

    @BeforeEach
    void setUp() {
        caller = UserContext.of(UUID.randomUUID(), "traveller@example.com", Role.USER);
        SupportedDestinationService destinations = mock(SupportedDestinationService.class);
        when(destinations.listSupported()).thenReturn(List.of());
        TripAccess access = new TripAccess(trips);
        briefService = new TripBriefService(access, trips, briefs, destinations);
        toolService = new TripChatToolService(briefService, access, conversations, objectMapper);
    }

    // ------------------------------------------------------------------------------------------
    // Which tools are offered.
    // ------------------------------------------------------------------------------------------

    @Test
    void aDraftTripOffersOnlyUpdateTripBrief() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        ScriptedLlm llm = ScriptedLlm.emitting(new LlmEvent.Done(StopReason.END_TURN));

        drain(orchestrator(llm).stream(turn()));

        assertThat(llm.offeredTools().getFirst()).extracting(ToolSpec::name)
                .containsExactly(TripChatTools.UPDATE_TRIP_BRIEF);
    }

    @Test
    void aClarificationNeededTripAlsoOffersAnswerClarification() {
        seedTrip(TripStatus.CLARIFICATION_NEEDED, TripBriefDetails.empty());
        ScriptedLlm llm = ScriptedLlm.emitting(new LlmEvent.Done(StopReason.END_TURN));

        drain(orchestrator(llm).stream(turn()));

        assertThat(llm.offeredTools().getFirst()).extracting(ToolSpec::name)
                .containsExactly(TripChatTools.UPDATE_TRIP_BRIEF, TripChatTools.ANSWER_CLARIFICATION);
    }

    @Test
    void aBriefCompleteTripOffersStartResearch() {
        seedTrip(TripStatus.BRIEF_COMPLETE, complete());
        ScriptedLlm llm = ScriptedLlm.emitting(new LlmEvent.Done(StopReason.END_TURN));

        drain(orchestrator(llm).stream(turn()));

        assertThat(llm.offeredTools().getFirst()).extracting(ToolSpec::name)
                .containsExactly(TripChatTools.START_RESEARCH);
    }

    @Test
    void anArchivedTripIsOfferedNoTools() {
        seedTrip(TripStatus.ARCHIVED, complete());
        ScriptedLlm llm = ScriptedLlm.emitting(new LlmEvent.Done(StopReason.END_TURN));

        drain(orchestrator(llm).stream(turn()));

        assertThat(llm.offeredTools().getFirst()).isEmpty();
    }

    @Test
    void thePromptCarriesTheStatusAndExpectedVersion() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        ScriptedLlm llm = ScriptedLlm.emitting(new LlmEvent.Done(StopReason.END_TURN));

        drain(orchestrator(llm).stream(turn()));

        String system = llm.prompts().getFirst().systemText();
        assertThat(system).contains("trip.status: DRAFT").contains("brief.expected_version: 3");
    }

    // ------------------------------------------------------------------------------------------
    // The happy path.
    // ------------------------------------------------------------------------------------------

    @Test
    void aValidUpdateEmitsAToolResultAndBriefUpdatedAndPersistsTheField() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        ScriptedLlm llm = updateCall("{\"expected_version\":3,\"departure_city\":\"Kuala Lumpur\"}");

        List<LlmEvent> events = drain(orchestrator(llm).stream(turn()));

        assertThat(events).anySatisfy(event -> assertThat(event)
                .isInstanceOf(LlmEvent.ToolResult.class));
        assertThat(briefUpdatedTripIds(events)).containsExactly(tripId.toString());
        assertThat(briefs.stored(tripId).departureCity()).isEqualTo("Kuala Lumpur");
        assertThat(conversations.stored).extracting(Message::role).contains(
                com.travelplanner.domain.enums.ChatMessageRole.TOOL_CALL,
                com.travelplanner.domain.enums.ChatMessageRole.TOOL_RESULT);
    }

    @Test
    void aValidUpdateReportsTheAppliedFieldsAndNewVersionInTheToolResult() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        ScriptedLlm llm = updateCall("{\"expected_version\":3,\"departure_city\":\"Kuala Lumpur\"}");

        String payload = toolResultPayload(drain(orchestrator(llm).stream(turn())));

        assertThat(payload).contains("\"departure_city\"").contains("\"version\":4")
                .contains("\"trip_id\":\"" + tripId + "\"");
    }

    @Test
    void answeringTheLastOutstandingQuestionReachesBriefComplete() {
        seedTrip(TripStatus.CLARIFICATION_NEEDED, completeExceptPace());
        ScriptedLlm llm = answerCall(
                "{\"expected_version\":3,\"answers\":[{\"question_id\":\"pace\",\"choice\":\"MODERATE\"}]}");

        drain(orchestrator(llm).stream(turn()));

        assertThat(trips.stored(tripId).status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
        assertThat(briefs.stored(tripId).pace()).isEqualTo(TravelPace.MODERATE);
    }

    // ------------------------------------------------------------------------------------------
    // Refusals: each is a typed error, and none mutates the brief.
    // ------------------------------------------------------------------------------------------

    @Test
    void updatingABriefCompleteTripIsRefusedByTheStatusGateAndMutatesNothing() {
        seedTrip(TripStatus.BRIEF_COMPLETE, complete());
        ScriptedLlm llm = updateCall("{\"expected_version\":3,\"departure_city\":\"Penang\"}");

        List<LlmEvent> events = drain(orchestrator(llm).stream(turn()));

        assertThat(streamErrorCode(events)).isEqualTo("validation_failed");
        assertThat(briefUpdatedTripIds(events)).isEmpty();
        assertThat(briefs.stored(tripId).departureCity()).isEqualTo("Kuala Lumpur");
        assertThat(briefs.stored(tripId).version()).isEqualTo(3);
    }

    @Test
    void answeringClarificationInDraftIsRefusedBecauseTheToolIsNotOfferedThere() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        ScriptedLlm llm = answerCall(
                "{\"expected_version\":3,\"answers\":[{\"question_id\":\"pace\",\"choice\":\"MODERATE\"}]}");

        List<LlmEvent> events = drain(orchestrator(llm).stream(turn()));

        assertThat(streamErrorCode(events)).isEqualTo("validation_failed");
        assertThat(briefs.stored(tripId).pace()).isNull();
    }

    @Test
    void anUnknownFieldIsRejectedAtParseTimeWithoutMutating() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        ScriptedLlm llm = updateCall("{\"expected_version\":3,\"colour\":\"blue\"}");

        List<LlmEvent> events = drain(orchestrator(llm).stream(turn()));

        assertThat(streamErrorCode(events)).isEqualTo("validation_failed");
        assertThat(briefUpdatedTripIds(events)).isEmpty();
        assertThat(conversations.stored).isEmpty();
    }

    @Test
    void anUnknownToolNameIsRejectedWithoutMutating() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        ScriptedLlm llm = ScriptedLlm.emitting(
                new LlmEvent.ToolUseStart("call-1", "delete_trip"),
                new LlmEvent.ToolInputDelta("call-1", "{}"),
                new LlmEvent.ToolUseEnd("call-1"),
                new LlmEvent.Done(StopReason.TOOL_USE));

        List<LlmEvent> events = drain(orchestrator(llm).stream(turn()));

        assertThat(streamErrorCode(events)).isEqualTo("validation_failed");
        assertThat(briefUpdatedTripIds(events)).isEmpty();
    }

    @Test
    void aStaleExpectedVersionSurfacesAsVersionConflict() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        ScriptedLlm llm = updateCall("{\"expected_version\":1,\"departure_city\":\"Kuala Lumpur\"}");

        List<LlmEvent> events = drain(orchestrator(llm).stream(turn()));

        assertThat(streamErrorCode(events)).isEqualTo(VersionConflictException.CODE);
        assertThat(briefs.stored(tripId).departureCity()).isNull();
    }

    @Test
    void anArchivedTripCannotBeMutated() {
        seedTrip(TripStatus.ARCHIVED, complete());
        ScriptedLlm llm = updateCall("{\"expected_version\":3,\"departure_city\":\"Penang\"}");

        List<LlmEvent> events = drain(orchestrator(llm).stream(turn()));

        assertThat(streamErrorCode(events)).isEqualTo("validation_failed");
        assertThat(briefs.stored(tripId).departureCity()).isEqualTo("Kuala Lumpur");
    }

    // ------------------------------------------------------------------------------------------
    // The tool loop.
    // ------------------------------------------------------------------------------------------

    @Test
    void aToolUseStopStartsAnotherRoundAndOnlyTheFinalDoneIsForwarded() {
        seedTrip(TripStatus.DRAFT, TripBriefDetails.empty());
        // Round 1 asks for a tool and stops on TOOL_USE; round 2 just talks and ends the turn.
        List<LlmEvent> round1 = List.of(
                new LlmEvent.ToolUseStart("call-1", TripChatTools.UPDATE_TRIP_BRIEF),
                new LlmEvent.ToolInputDelta("call-1",
                        "{\"expected_version\":3,\"departure_city\":\"Kuala Lumpur\"}"),
                new LlmEvent.ToolUseEnd("call-1"),
                new LlmEvent.Done(StopReason.TOOL_USE));
        List<LlmEvent> round2 = List.of(
                new LlmEvent.TextDelta("Saved your departure city."),
                new LlmEvent.Done(StopReason.END_TURN));
        ScriptedLlm llm = ScriptedLlm.scripted(round1, round2);

        List<LlmEvent> events = drain(orchestrator(llm).stream(turn()));

        assertThat(llm.prompts()).hasSize(2);
        List<LlmEvent.Done> dones = events.stream()
                .filter(LlmEvent.Done.class::isInstance).map(LlmEvent.Done.class::cast).toList();
        assertThat(dones).extracting(LlmEvent.Done::stopReason).containsExactly(StopReason.END_TURN);
        assertThat(llm.prompts().get(1).messages()).extracting(PromptMessage::role)
                .contains(com.travelplanner.domain.ai.MessageRole.TOOL);
    }

    // ------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------

    private TripChatOrchestrator orchestrator(LlmStreamPort llm) {
        return new TripChatOrchestrator(llm, toolService, mock(TripChatResearchToolService.class),
                briefService, objectMapper);
    }

    private ChatTurn turn() {
        Prompt prompt = Prompt.adHoc(List.of(PromptMessage.system("base"),
                PromptMessage.user("Fly from Kuala Lumpur.")));
        Message user = Message.fromUser(conversationId, 1L, "Fly from Kuala Lumpur.", "cmid-1", NOW);
        Message assistant = Message.assistantStreamStarted(conversationId, 2L, NOW);
        return new ChatTurn(conversationId, ChatTarget.trip(tripId), caller, user, assistant, prompt);
    }

    private void seedTrip(TripStatus status, TripBriefDetails details) {
        tripId = UUID.randomUUID();
        trips.seed(new Trip(tripId, caller.userId(), "Japan in spring", status, null, 2, NOW, NOW));
        briefs.seed(new TripBrief(UUID.randomUUID(), tripId, details.destinations(),
                details.surpriseMe(), details.dates(), details.dateFlexibility(),
                details.departureCity(), details.budget(), details.party(), details.interests(),
                details.pace(), 3, NOW, NOW));
        Conversation conversation = Conversation.startForTrip(caller.userId(), tripId, NOW);
        conversationId = conversation.id();
        conversations.seed(conversation);
    }

    private ScriptedLlm updateCall(String argsJson) {
        return ScriptedLlm.emitting(
                new LlmEvent.ToolUseStart("call-1", TripChatTools.UPDATE_TRIP_BRIEF),
                new LlmEvent.ToolInputDelta("call-1", argsJson),
                new LlmEvent.ToolUseEnd("call-1"),
                new LlmEvent.Done(StopReason.TOOL_USE));
    }

    private ScriptedLlm answerCall(String argsJson) {
        return ScriptedLlm.emitting(
                new LlmEvent.ToolUseStart("call-1", TripChatTools.ANSWER_CLARIFICATION),
                new LlmEvent.ToolInputDelta("call-1", argsJson),
                new LlmEvent.ToolUseEnd("call-1"),
                new LlmEvent.Done(StopReason.TOOL_USE));
    }

    private static TripBriefDetails complete() {
        return completeExceptPace().withPace(TravelPace.MODERATE);
    }

    private static TripBriefDetails completeExceptPace() {
        return TripBriefDetails.empty()
                .withDates(SPRING)
                .withDateFlexibility(DateFlexibility.FLEXIBLE_WEEK)
                .withDepartureCity("Kuala Lumpur")
                .withBudget(Money.of("4000.00", "MYR"))
                .withParty(new PartySize(2, 0))
                .withInterests(List.of(TravelInterest.FOOD));
    }

    private static List<LlmEvent> drain(Flux<LlmEvent> stream) {
        return stream.collectList().block(Duration.ofSeconds(10));
    }

    private static String streamErrorCode(List<LlmEvent> events) {
        return events.stream().filter(LlmEvent.StreamError.class::isInstance)
                .map(LlmEvent.StreamError.class::cast).map(LlmEvent.StreamError::code)
                .findFirst().orElse(null);
    }

    private static List<String> briefUpdatedTripIds(List<LlmEvent> events) {
        return events.stream().filter(LlmEvent.DomainEvent.class::isInstance)
                .map(LlmEvent.DomainEvent.class::cast)
                .filter(event -> "brief_updated".equals(event.type()))
                .map(event -> String.valueOf(event.payload().get("trip_id")))
                .toList();
    }

    private static String toolResultPayload(List<LlmEvent> events) {
        return events.stream().filter(LlmEvent.ToolResult.class::isInstance)
                .map(LlmEvent.ToolResult.class::cast).map(LlmEvent.ToolResult::payload)
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------------------------------

    /** Streams a scripted list of provider events per round, recording prompts and offered tools. */
    private static final class ScriptedLlm implements LlmStreamPort {

        private final List<Supplier<Flux<LlmEvent>>> rounds = new ArrayList<>();
        private final List<Prompt> prompts = new ArrayList<>();
        private final List<List<ToolSpec>> offeredTools = new ArrayList<>();
        private int round;

        private ScriptedLlm(List<Supplier<Flux<LlmEvent>>> rounds) {
            this.rounds.addAll(rounds);
        }

        private static ScriptedLlm emitting(LlmEvent... events) {
            return new ScriptedLlm(List.of(() -> Flux.fromArray(events)));
        }

        private static ScriptedLlm scripted(List<LlmEvent> first, List<LlmEvent> second) {
            return new ScriptedLlm(List.of(() -> Flux.fromIterable(first),
                    () -> Flux.fromIterable(second)));
        }

        private List<Prompt> prompts() {
            return List.copyOf(prompts);
        }

        private List<List<ToolSpec>> offeredTools() {
            return List.copyOf(offeredTools);
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
            return stream(prompt, List.of(), options);
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
            prompts.add(prompt);
            offeredTools.add(List.copyOf(tools));
            int current = Math.min(round++, rounds.size() - 1);
            return Flux.defer(rounds.get(current));
        }
    }

    /** {@link TripRepositoryPort} with the ADR 008 optimistic lock modelled. */
    private static final class InMemoryTrips implements TripRepositoryPort {

        private final Map<UUID, Trip> rows = new LinkedHashMap<>();

        void seed(Trip trip) {
            rows.put(trip.id(), trip);
        }

        Trip stored(UUID id) {
            return rows.get(id);
        }

        @Override
        public Trip save(Trip trip) {
            Trip current = rows.get(trip.id());
            if (current != null && current.version() != trip.version()) {
                throw new VersionConflictException(current.version());
            }
            Trip persisted = new Trip(trip.id(), trip.userId(), trip.name(), trip.status(),
                    trip.selectedRecommendationId(), trip.version() + 1, trip.createdAt(),
                    trip.updatedAt());
            rows.put(persisted.id(), persisted);
            return persisted;
        }

        @Override
        public Optional<Trip> findByIdAndUserId(UUID id, UUID userId) {
            return Optional.ofNullable(rows.get(id)).filter(trip -> trip.isOwnedBy(userId));
        }

        @Override
        public List<Trip> findAllByUserId(UUID userId) {
            return rows.values().stream().filter(trip -> trip.isOwnedBy(userId)).toList();
        }

        @Override
        public boolean deleteByIdAndUserId(UUID id, UUID userId) {
            return findByIdAndUserId(id, userId).map(trip -> rows.remove(id) != null).orElse(false);
        }
    }

    /** {@link TripBriefRepositoryPort} with the same optimistic lock. */
    private static final class InMemoryBriefs implements TripBriefRepositoryPort {

        private final Map<UUID, TripBrief> byTripId = new LinkedHashMap<>();

        void seed(TripBrief brief) {
            byTripId.put(brief.tripId(), brief);
        }

        TripBrief stored(UUID id) {
            return byTripId.get(id);
        }

        @Override
        public TripBrief save(TripBrief brief) {
            TripBrief current = byTripId.get(brief.tripId());
            if (current != null && current.version() != brief.version()) {
                throw new VersionConflictException(current.version());
            }
            TripBrief persisted = new TripBrief(brief.id(), brief.tripId(), brief.destinations(),
                    brief.surpriseMe(), brief.dates(), brief.dateFlexibility(), brief.departureCity(),
                    brief.budget(), brief.party(), brief.interests(), brief.pace(),
                    brief.version() + 1, brief.createdAt(), brief.updatedAt());
            byTripId.put(persisted.tripId(), persisted);
            return persisted;
        }

        @Override
        public Optional<TripBrief> findByTripId(UUID id) {
            return Optional.ofNullable(byTripId.get(id));
        }
    }

    /** Only the four calls {@link TripChatToolService} makes are meaningful; the rest are inert. */
    private static final class ConversationFake implements ConversationRepositoryPort {

        private final Map<UUID, Conversation> conversationsById = new LinkedHashMap<>();
        private final List<Message> stored = new ArrayList<>();
        private long nextSeq = 1L;

        void seed(Conversation conversation) {
            conversationsById.put(conversation.id(), conversation);
        }

        @Override
        public long allocateSequence(UUID conversationId, UUID userId) {
            return nextSeq++;
        }

        @Override
        public Message appendMessage(Message message) {
            stored.add(message);
            return message;
        }

        @Override
        public Optional<Conversation> findConversationByIdAndUserId(UUID conversationId, UUID userId) {
            return Optional.ofNullable(conversationsById.get(conversationId))
                    .filter(conversation -> conversation.isOwnedBy(userId));
        }

        @Override
        public Conversation saveConversation(Conversation conversation) {
            conversationsById.put(conversation.id(), conversation);
            return conversation;
        }

        @Override
        public PlannerSession savePlannerSession(PlannerSession session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PlannerSession> findOpenPlannerSession(UUID userId) {
            return Optional.empty();
        }

        @Override
        public Optional<Conversation> findConversationByTripIdAndUserId(UUID tripId, UUID userId) {
            return Optional.empty();
        }

        @Override
        public Optional<Conversation> findConversationByPlannerSessionIdAndUserId(UUID id, UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<Conversation> findConversationsByUserId(UUID userId, int pageNumber, int pageSize) {
            return List.of();
        }

        @Override
        public Message saveMessage(Message message) {
            stored.add(message);
            return message;
        }

        @Override
        public Optional<Message> findMessageByClientMessageId(UUID id, UUID userId, String clientId) {
            return Optional.empty();
        }

        @Override
        public List<Message> findMessagesAfter(UUID id, UUID userId, long afterSeq, int limit) {
            return stored.stream().filter(message -> message.seq() > afterSeq)
                    .sorted(Comparator.comparingLong(Message::seq)).limit(limit).toList();
        }

        @Override
        public List<Message> findLatestMessages(UUID id, UUID userId, int limit) {
            return List.copyOf(stored);
        }

        @Override
        public long countMessages(UUID id, UUID userId) {
            return stored.size();
        }
    }
}
