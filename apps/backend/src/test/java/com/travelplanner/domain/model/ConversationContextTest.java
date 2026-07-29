package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.ConversationScope;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class ConversationContextTest {

    private static final Instant NOW = Instant.parse("2026-07-29T09:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID TRIP = UUID.randomUUID();

    @Test
    void isBuiltFromAConversationAndTheHistoryAlreadyLoadedInOrder() {
        Conversation conversation = Conversation.startForTrip(OWNER, TRIP, NOW);
        List<Message> history = List.of(
                Message.fromUser(conversation.id(), 1L, "Japan in spring", "c-1", NOW),
                Message.assistantStreamStarted(conversation.id(), 2L, NOW).complete(NOW));

        ConversationContext context = ConversationContext.of(conversation, history, false);

        assertThat(context.conversationId()).isEqualTo(conversation.id());
        assertThat(context.scope()).isEqualTo(ConversationScope.TRIP);
        assertThat(context.tripIfPresent()).contains(TRIP);
        assertThat(context.history()).hasSize(2);
        assertThat(context.isEmpty()).isFalse();
        assertThat(context.truncated()).isFalse();
    }

    @Test
    void aPlannerContextHasNoTrip() {
        Conversation planner = Conversation.startPlanner(OWNER, SESSION, NOW);

        ConversationContext context = ConversationContext.of(planner, List.of(), false);

        assertThat(context.scope()).isEqualTo(ConversationScope.PLANNER);
        assertThat(context.tripIfPresent()).isEmpty();
        assertThat(context.isEmpty())
                .describedAs("the very first turn of a thread has nothing to recall")
                .isTrue();
        assertThat(context.latestSeq()).isEmpty();
    }

    @Test
    void theLatestSequenceComesFromTheDataSoItCannotDrift() {
        // It is the ADR 007 resume cursor. Tracking it separately from the history it describes is
        // how a client ends up resuming from a frame that was never sent.
        Conversation conversation = Conversation.startForTrip(OWNER, TRIP, NOW);
        List<Message> history = List.of(
                Message.system(conversation.id(), 7L, "a", NOW),
                Message.system(conversation.id(), 8L, "b", NOW),
                Message.system(conversation.id(), 9L, "c", NOW));

        assertThat(ConversationContext.of(conversation, history, true).latestSeq()).contains(9L);
    }

    @Test
    void truncationIsReportedRatherThanBeingLeftInvisible() {
        // A window that dropped the oldest turns and one that happens to hold the whole thread look
        // identical from the message list alone. The flag is what stops "the agent forgot what I
        // said" from being undiagnosable.
        Conversation conversation = Conversation.startForTrip(OWNER, TRIP, NOW);
        List<Message> window = List.of(Message.system(conversation.id(), 400L, "recent", NOW));

        assertThat(ConversationContext.of(conversation, window, true).truncated()).isTrue();
        assertThat(ConversationContext.of(conversation, window, false).truncated()).isFalse();
    }

    @Test
    void theHistoryIsASnapshotThatTheCallerCannotEditAfterwards() {
        Conversation conversation = Conversation.startForTrip(OWNER, TRIP, NOW);
        List<Message> mutable = new ArrayList<>();
        mutable.add(Message.system(conversation.id(), 1L, "a", NOW));

        ConversationContext context = ConversationContext.of(conversation, mutable, false);
        mutable.add(Message.system(conversation.id(), 2L, "b", NOW));

        assertThat(context.history()).hasSize(1);
        assertThatThrownBy(() -> context.history().add(Message.system(conversation.id(), 3L, "c", NOW)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullHistoryBecomesAnEmptyWindow() {
        Conversation conversation = Conversation.startForTrip(OWNER, TRIP, NOW);

        assertThat(ConversationContext.of(conversation, null, false).history()).isEmpty();
    }

    @Test
    void scopeAndTripLinkageMustAgreeInBothDirections() {
        UUID conversationId = UUID.randomUUID();

        assertThatThrownBy(() -> new ConversationContext(conversationId, ConversationScope.TRIP,
                null, List.of(), false))
                .describedAs("a TRIP context with no trip")
                .isInstanceOf(ValidationFailedException.class);

        assertThatThrownBy(() -> new ConversationContext(conversationId, ConversationScope.PLANNER,
                TRIP, List.of(), false))
                .describedAs("a PLANNER context that acquired a trip")
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void everyIdentifyingFieldIsRequired() {
        UUID conversationId = UUID.randomUUID();

        assertThatThrownBy(() -> new ConversationContext(null, ConversationScope.PLANNER,
                null, List.of(), false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConversationContext(conversationId, null,
                null, List.of(), false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ConversationContext.of(null, List.of(), false))
                .isInstanceOf(NullPointerException.class);
    }
}
