package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.ConversationScope;
import com.travelplanner.domain.enums.ConversationState;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class ConversationTest {

    private static final Instant NOW = Instant.parse("2026-07-29T09:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID TRIP = UUID.randomUUID();

    // -------------------------------------------------------------------------------------
    // Construction and the two shapes.
    // -------------------------------------------------------------------------------------

    @Test
    void aPlannerConversationStartsActiveWithNoTripAtSequenceOne() {
        Conversation conversation = Conversation.startPlanner(OWNER, SESSION, NOW);

        assertThat(conversation.id()).isNotNull();
        assertThat(conversation.scope()).isEqualTo(ConversationScope.PLANNER);
        assertThat(conversation.state()).isEqualTo(ConversationState.ACTIVE);
        assertThat(conversation.tripIfPresent()).isEmpty();
        assertThat(conversation.plannerSessionIfPresent()).contains(SESSION);
        assertThat(conversation.nextMessageSeq()).isEqualTo(Conversation.FIRST_SEQ);
        assertThat(conversation.lastMessageAtIfPresent()).isEmpty();
        assertThat(conversation.isAppendable()).isTrue();
    }

    @Test
    void aTripConversationStartsAttachedWithNoPlannerSession() {
        Conversation conversation = Conversation.startForTrip(OWNER, TRIP, NOW);

        assertThat(conversation.scope()).isEqualTo(ConversationScope.TRIP);
        assertThat(conversation.tripIfPresent()).contains(TRIP);
        assertThat(conversation.plannerSessionIfPresent()).isEmpty();
        assertThat(conversation.nextMessageSeq()).isEqualTo(Conversation.FIRST_SEQ);
    }

    @Test
    void bothFactoriesRefuseTheIdentifierTheirShapeDependsOn() {
        assertThatThrownBy(() -> Conversation.startPlanner(OWNER, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Conversation.startForTrip(OWNER, null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void scopeAndTripLinkageMustAgreeInBothDirections() {
        // Mirrors ck_conversation_scope_matches_trip, written as an equivalence so BOTH mistakes
        // are caught rather than only the obvious one.
        assertThatThrownBy(() -> conversation(ConversationScope.TRIP, null,
                ConversationState.ACTIVE, null, Conversation.FIRST_SEQ))
                .describedAs("a TRIP conversation with no trip")
                .isInstanceOf(ValidationFailedException.class);

        assertThatThrownBy(() -> conversation(ConversationScope.PLANNER, TRIP,
                ConversationState.ACTIVE, null, Conversation.FIRST_SEQ))
                .describedAs("a PLANNER conversation that quietly acquired a trip")
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void archivedStateAndItsTimestampMustAgreeInBothDirections() {
        assertThatThrownBy(() -> conversation(ConversationScope.PLANNER, null,
                ConversationState.ARCHIVED, null, Conversation.FIRST_SEQ))
                .describedAs("archived with no record of when")
                .isInstanceOf(ValidationFailedException.class);

        assertThatThrownBy(() -> conversation(ConversationScope.PLANNER, null,
                ConversationState.ACTIVE, NOW, Conversation.FIRST_SEQ))
                .describedAs("an archived_at on an active thread")
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void theSequenceCounterCanNeverStartBelowOne() {
        // seq 0 would collide with the "start from the beginning" cursor value that findMessagesAfter
        // documents, so the first number handed out has to be 1.
        assertThatThrownBy(() -> conversation(ConversationScope.PLANNER, null,
                ConversationState.ACTIVE, null, 0L))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void everyIdentifyingFieldIsRequired() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new Conversation(null, OWNER, null, SESSION,
                ConversationScope.PLANNER, ConversationState.ACTIVE, 1L, null, null, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Conversation(id, null, null, SESSION,
                ConversationScope.PLANNER, ConversationState.ACTIVE, 1L, null, null, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Conversation(id, OWNER, null, SESSION,
                null, ConversationState.ACTIVE, 1L, null, null, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Conversation(id, OWNER, null, SESSION,
                ConversationScope.PLANNER, null, 1L, null, null, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Conversation(id, OWNER, null, SESSION,
                ConversationScope.PLANNER, ConversationState.ACTIVE, 1L, null, null, null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Conversation(id, OWNER, null, SESSION,
                ConversationScope.PLANNER, ConversationState.ACTIVE, 1L, null, null, NOW, null))
                .isInstanceOf(NullPointerException.class);
    }

    // -------------------------------------------------------------------------------------
    // Ownership (PLAN §4.0.2-L).
    // -------------------------------------------------------------------------------------

    @Test
    void isOwnedByAnswersTheUserScopingQuestion() {
        Conversation conversation = Conversation.startPlanner(OWNER, SESSION, NOW);

        assertThat(conversation.isOwnedBy(OWNER)).isTrue();
        assertThat(conversation.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    // -------------------------------------------------------------------------------------
    // The create_trip handoff.
    // -------------------------------------------------------------------------------------

    @Test
    void linkingToATripConvertsThePlannerThreadAndKeepsItsProvenance() {
        Conversation planner = Conversation.startPlanner(OWNER, SESSION, NOW);
        Instant later = NOW.plusSeconds(120);

        Conversation linked = planner.linkToTrip(TRIP, later);

        assertThat(linked.scope()).isEqualTo(ConversationScope.TRIP);
        assertThat(linked.tripIfPresent()).contains(TRIP);
        assertThat(linked.plannerSessionIfPresent())
                .describedAs("where the trip came from stays answerable after the handoff")
                .contains(SESSION);
        assertThat(linked.id()).isEqualTo(planner.id());
        assertThat(linked.updatedAt()).isEqualTo(later);
        assertThat(planner.scope())
                .describedAs("the original instance must not be mutated")
                .isEqualTo(ConversationScope.PLANNER);
    }

    @Test
    void aConversationAlreadyLinkedToATripCannotBeRelinked() {
        // Re-linking would move one trip's history onto another trip.
        Conversation linked = Conversation.startForTrip(OWNER, TRIP, NOW);

        assertThatThrownBy(() -> linked.linkToTrip(UUID.randomUUID(), NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void linkingRefusesANullTripAndAnArchivedThread() {
        Conversation planner = Conversation.startPlanner(OWNER, SESSION, NOW);
        Conversation archived = planner.archive(NOW);

        assertThatThrownBy(() -> planner.linkToTrip(null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> archived.linkToTrip(TRIP, NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    // -------------------------------------------------------------------------------------
    // Archiving and read-only behaviour.
    // -------------------------------------------------------------------------------------

    @Test
    void archivingMakesTheThreadReadOnlyAndStampsWhen() {
        Conversation active = Conversation.startForTrip(OWNER, TRIP, NOW);
        Instant later = NOW.plusSeconds(3600);

        Conversation archived = active.archive(later);

        assertThat(archived.state()).isEqualTo(ConversationState.ARCHIVED);
        assertThat(archived.archivedAt()).isEqualTo(later);
        assertThat(archived.isAppendable()).isFalse();
        assertThat(active.isAppendable())
                .describedAs("the original instance must not be mutated")
                .isTrue();
    }

    @Test
    void anArchivedConversationRefusesEveryAppendPath() {
        Conversation archived = Conversation.startForTrip(OWNER, TRIP, NOW).archive(NOW);

        assertThatThrownBy(archived::requireAppendable).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> archived.recordAppend(NOW)).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> archived.archive(NOW)).isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void requireAppendablePassesSilentlyOnAnActiveThread() {
        assertThatCode(Conversation.startForTrip(OWNER, TRIP, NOW)::requireAppendable)
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------------------
    // Ordering — the property tasks/20 exists to guarantee.
    // -------------------------------------------------------------------------------------

    @Test
    void eachAppendConsumesOneSequenceNumberAndAdvancesTheCounter() {
        Conversation conversation = Conversation.startPlanner(OWNER, SESSION, NOW);

        long first = conversation.nextMessageSeq();
        Conversation afterFirst = conversation.recordAppend(NOW);
        long second = afterFirst.nextMessageSeq();
        Conversation afterSecond = afterFirst.recordAppend(NOW.plusSeconds(1));

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(afterSecond.nextMessageSeq()).isEqualTo(3L);
    }

    @Test
    void theSequenceIsStrictlyMonotonicOverALongThread() {
        // The guarantee timestamps cannot give: Postgres fixes now() per transaction, so every row
        // one turn writes shares a value. Only a counter distinguishes them.
        Conversation conversation = Conversation.startPlanner(OWNER, SESSION, NOW);
        long previous = 0L;

        for (int append = 0; append < 250; append++) {
            long allocated = conversation.nextMessageSeq();
            assertThat(allocated).isGreaterThan(previous);
            previous = allocated;
            // Same instant every time on purpose — the sequence must not depend on the clock.
            conversation = conversation.recordAppend(NOW);
        }

        assertThat(conversation.nextMessageSeq()).isEqualTo(251L);
    }

    @Test
    void recordingAnAppendAdvancesTheConversationListOrderingColumn() {
        Conversation conversation = Conversation.startPlanner(OWNER, SESSION, NOW);
        Instant later = NOW.plusSeconds(45);

        Conversation appended = conversation.recordAppend(later);

        assertThat(appended.lastMessageAtIfPresent()).contains(later);
        assertThat(appended.updatedAt()).isEqualTo(later);
        assertThat(appended.createdAt()).isEqualTo(NOW);
    }

    private static Conversation conversation(ConversationScope scope, UUID tripId,
            ConversationState state, Instant archivedAt, long nextSeq) {
        return new Conversation(UUID.randomUUID(), OWNER, tripId, SESSION, scope, state,
                nextSeq, null, archivedAt, NOW, NOW);
    }
}
