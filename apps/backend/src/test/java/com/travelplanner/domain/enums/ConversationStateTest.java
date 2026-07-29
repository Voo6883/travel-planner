package com.travelplanner.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class ConversationStateTest {

    @Test
    void listsExactlyTheActiveAndArchivedStates() {
        assertThat(ConversationState.values())
                .containsExactlyInAnyOrder(ConversationState.ACTIVE, ConversationState.ARCHIVED);
    }

    @Test
    void onlyAnArchivedConversationIsReadOnly() {
        assertThat(ConversationState.ACTIVE.isReadOnly()).isFalse();
        assertThat(ConversationState.ARCHIVED.isReadOnly()).isTrue();
        assertThat(Stream.of(ConversationState.values()).filter(ConversationState::isReadOnly))
                .containsExactly(ConversationState.ARCHIVED);
    }

    @Test
    void matchesTheReadOnlyRuleAnArchivedTripAlreadyHas() {
        // Deliberately the same shape as TripStatus.ARCHIVED: one read-only concept, not two, so a
        // user who archives a trip does not have to learn that its chat behaves differently.
        assertThat(ConversationState.ARCHIVED.isReadOnly()).isEqualTo(TripStatus.ARCHIVED.isReadOnly());
    }
}
