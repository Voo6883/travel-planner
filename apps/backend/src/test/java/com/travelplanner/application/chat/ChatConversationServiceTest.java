package com.travelplanner.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.application.chat.ChatTestFakes.ConversationRepositoryFake;
import com.travelplanner.application.chat.ChatTestFakes.TripRepositoryFake;
import com.travelplanner.application.page.PageQuery;
import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ConversationNotFoundException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Conversation;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The persistence half of a chat turn: resolution, ownership, idempotency, and history order.
 *
 * <p>Every assertion here is about a rule that has a database constraint behind it. That is
 * deliberate — the Testcontainers suite proves the constraint exists, and these tests prove the
 * service relies on it rather than reimplementing it approximately.
 */
class ChatConversationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    private final ConversationRepositoryFake repository = new ConversationRepositoryFake();
    private final TripRepositoryFake trips = new TripRepositoryFake();
    private ChatConversationService service;
    private UserContext caller;
    private UserContext stranger;

    @BeforeEach
    void setUp() {
        service = new ChatConversationService(repository, trips);
        caller = ChatTestFakes.user(UUID.randomUUID());
        stranger = ChatTestFakes.user(UUID.randomUUID());
    }

    // ------------------------------------------------------------------------------------------
    // Resolution.
    // ------------------------------------------------------------------------------------------

    @Test
    void theFirstPlannerSendOpensASessionAndItsConversation() {
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);

        assertThat(conversation.plannerSessionIfPresent()).isPresent();
        assertThat(conversation.tripIfPresent()).isEmpty();
        assertThat(repository.findOpenPlannerSession(caller.userId())).isPresent();
    }

    @Test
    void theSecondPlannerSendResumesTheSameConversation() {
        // Not a new thread per message: `uq_planner_session_user_open` allows one open session, and
        // a second conversation under it would split the user's chat in half.
        Conversation first = service.resolveForAppend(ChatTarget.planner(), null, caller);
        Conversation second = service.resolveForAppend(ChatTarget.planner(), null, caller);

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void aTripConversationIsOpenedOnFirstUseAndOnlyForAnOwnedTrip() {
        Trip trip = trips.add(ownedTrip(caller));

        Conversation conversation = service.resolveForAppend(ChatTarget.trip(trip.id()), null, caller);

        assertThat(conversation.tripIfPresent()).contains(trip.id());
    }

    @Test
    void chattingOnAnotherUsersTripIsNotFoundAndCreatesNothing() {
        // A 403 here would confirm the trip exists. It also must not leave a conversation row
        // pointing at somebody else's trip behind, which is why the ownership read comes first.
        Trip trip = trips.add(ownedTrip(stranger));

        assertThatThrownBy(() -> service.resolveForAppend(ChatTarget.trip(trip.id()), null, caller))
                .isInstanceOf(TripNotFoundException.class);
        assertThat(repository.findConversationByTripIdAndUserId(trip.id(), stranger.userId())).isEmpty();
    }

    @Test
    void anotherUsersConversationIdIsNotFound() {
        Conversation theirs = service.resolveForAppend(ChatTarget.planner(), null, stranger);

        assertThatThrownBy(() -> service.resolveForAppend(ChatTarget.planner(), theirs.id(), caller))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void aTripConversationCannotBeDrivenThroughThePlannerPath() {
        // The surface a client is on has to predict which thread it writes to, or the planner home
        // could silently append to a trip's history.
        Trip trip = trips.add(ownedTrip(caller));
        Conversation tripThread = service.resolveForAppend(ChatTarget.trip(trip.id()), null, caller);

        assertThatThrownBy(() -> service.resolveForAppend(ChatTarget.planner(), tripThread.id(), caller))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void anArchivedConversationRefusesTheAppendBeforeAnythingIsWritten() {
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);
        repository.saveConversation(conversation.archive(NOW));

        assertThatThrownBy(() ->
                service.resolveForAppend(ChatTarget.planner(), conversation.id(), caller))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(repository.messagesOf(conversation.id())).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Idempotency — tasks/20 DoD: "disconnect/retry cannot duplicate committed user messages".
    // ------------------------------------------------------------------------------------------

    @Test
    void aDuplicateClientMessageIdDoesNotDoubleCommit() {
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);

        Message first = service.appendUserMessage(conversation.id(), "cmid-1", "Kyoto in spring", caller);
        Message retried = service.appendUserMessage(conversation.id(), "cmid-1", "Kyoto in spring", caller);

        assertThat(retried.id()).isEqualTo(first.id());
        assertThat(repository.messagesOf(conversation.id())).hasSize(1);
    }

    @Test
    void aRetriedSendConsumesNoSequenceNumber() {
        // Allocating and discarding a number would leave a permanent hole in the conversation's
        // ordering for every retry a flaky connection produced.
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);
        service.appendUserMessage(conversation.id(), "cmid-1", "one", caller);
        service.appendUserMessage(conversation.id(), "cmid-1", "one", caller);

        Message next = service.appendUserMessage(conversation.id(), "cmid-2", "two", caller);

        assertThat(next.seq()).isEqualTo(2L);
    }

    @Test
    void twoDifferentSendsBothCommitInOrder() {
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);

        service.appendUserMessage(conversation.id(), "cmid-1", "one", caller);
        service.appendUserMessage(conversation.id(), "cmid-2", "two", caller);

        assertThat(repository.messagesOf(conversation.id()))
                .extracting(Message::content)
                .containsExactly("one", "two");
    }

    // ------------------------------------------------------------------------------------------
    // The assistant row.
    // ------------------------------------------------------------------------------------------

    @Test
    void theAssistantRowIsPersistedEmptyAndStreamingBeforeAnyToken() {
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);

        Message opened = service.openAssistantMessage(conversation.id(), caller);

        assertThat(opened.role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(opened.status()).isEqualTo(ChatMessageStatus.STREAMING);
        assertThat(opened.content()).isEmpty();
        assertThat(opened.completedAtIfPresent()).isEmpty();
    }

    @Test
    void settlingAnInterruptedTurnKeepsTheTextThatArrived() {
        // ADR 007: "never silently discarded". The user watched these words arrive.
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);
        Message opened = service.openAssistantMessage(conversation.id(), caller);

        Message settled = service.settleAssistantMessage(opened, ChatMessageStatus.INTERRUPTED, "Kyoto in");

        assertThat(settled.status()).isEqualTo(ChatMessageStatus.INTERRUPTED);
        assertThat(settled.content()).isEqualTo("Kyoto in");
        assertThat(settled.completedAtIfPresent()).isPresent();
        assertThat(repository.message(opened.id()).orElseThrow().status())
                .isEqualTo(ChatMessageStatus.INTERRUPTED);
    }

    @Test
    void settlingRefusesTheOneStatusThatIsNotAnEnding() {
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);
        Message opened = service.openAssistantMessage(conversation.id(), caller);

        assertThatThrownBy(() ->
                service.settleAssistantMessage(opened, ChatMessageStatus.STREAMING, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theContextWindowIsBoundedAndOldestFirst() {
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);
        for (int index = 0; index < ChatConversationService.CONTEXT_WINDOW + 5; index++) {
            service.appendUserMessage(conversation.id(), "cmid-" + index, "message " + index, caller);
        }

        List<Message> window = service.contextWindow(conversation.id(), caller);

        assertThat(window).hasSize(ChatConversationService.CONTEXT_WINDOW);
        assertThat(window.get(0).content()).isEqualTo("message 5");
        assertThat(window).isSortedAccordingTo((left, right) -> Long.compare(left.seq(), right.seq()));
    }

    // ------------------------------------------------------------------------------------------
    // History.
    // ------------------------------------------------------------------------------------------

    @Test
    void historyForACallerWithNoConversationIsAnEmptyPageRatherThanA404() {
        ChatHistoryPage page = service.history(ChatTarget.planner(), null, PageQuery.of(0, 30, null), caller);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.conversationId()).isNull();
    }

    @Test
    void historyReturnsSeqOrderWithinThePage() {
        Conversation conversation = seed(6);

        ChatHistoryPage page = service.history(ChatTarget.planner(), null, PageQuery.of(0, 30, null), caller);

        assertThat(page.items()).extracting(Message::content)
                .containsExactly("m0", "m1", "m2", "m3", "m4", "m5");
        assertThat(page.total()).isEqualTo(6L);
        assertThat(page.conversationId()).isEqualTo(conversation.id());
    }

    @Test
    void pageZeroIsTheNewestPageAndLaterPagesAreOlder() {
        // What `loadOlderMessages()` asks for: the panel opens at the bottom and pages backwards.
        seed(7);

        ChatHistoryPage newest = service.history(ChatTarget.planner(), null, PageQuery.of(0, 3, null), caller);
        ChatHistoryPage middle = service.history(ChatTarget.planner(), null, PageQuery.of(1, 3, null), caller);
        ChatHistoryPage oldest = service.history(ChatTarget.planner(), null, PageQuery.of(2, 3, null), caller);

        assertThat(newest.items()).extracting(Message::content).containsExactly("m4", "m5", "m6");
        assertThat(middle.items()).extracting(Message::content).containsExactly("m1", "m2", "m3");
        assertThat(oldest.items()).extracting(Message::content).containsExactly("m0");
    }

    @Test
    void aPageBeyondTheEndIsEmptyRatherThanTheOldestPageAgain() {
        seed(4);

        ChatHistoryPage page = service.history(ChatTarget.planner(), null, PageQuery.of(9, 3, null), caller);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(4L);
    }

    @Test
    void historyOfAnotherUsersTripIsNotFoundRatherThanEmpty() {
        // An empty history would confirm the id exists; a 404 answers nothing either way.
        Trip trip = trips.add(ownedTrip(stranger));

        assertThatThrownBy(() ->
                service.history(ChatTarget.trip(trip.id()), null, PageQuery.of(0, 30, null), caller))
                .isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void historyOfAnOwnedTripWithNoConversationYetIsAnEmptyPage() {
        Trip trip = trips.add(ownedTrip(caller));

        ChatHistoryPage page =
                service.history(ChatTarget.trip(trip.id()), null, PageQuery.of(0, 30, null), caller);

        assertThat(page.items()).isEmpty();
        assertThat(page.conversationId()).isNull();
    }

    @Test
    void anArchivedConversationStillLoadsItsHistory() {
        // Archiving is not deletion. A user who archived a trip has not asked to lose what was said.
        Conversation conversation = seed(2);
        repository.saveConversation(
                repository.findConversationByIdAndUserId(conversation.id(), caller.userId())
                        .orElseThrow().archive(NOW));

        ChatHistoryPage page =
                service.history(ChatTarget.planner(), conversation.id(), PageQuery.of(0, 30, null), caller);

        assertThat(page.items()).hasSize(2);
    }

    private Conversation seed(int messages) {
        Conversation conversation = service.resolveForAppend(ChatTarget.planner(), null, caller);
        for (int index = 0; index < messages; index++) {
            service.appendUserMessage(conversation.id(), "cmid-" + index, "m" + index, caller);
        }
        return conversation;
    }

    private static Trip ownedTrip(UserContext owner) {
        return new Trip(UUID.randomUUID(), owner.userId(), "Kyoto", TripStatus.DRAFT, null, 0, NOW, NOW);
    }
}
