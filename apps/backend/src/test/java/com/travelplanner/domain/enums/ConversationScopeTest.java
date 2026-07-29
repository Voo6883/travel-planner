package com.travelplanner.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class ConversationScopeTest {

    @Test
    void listsExactlyThePlannerAndTripShapes() {
        assertThat(ConversationScope.values())
                .containsExactlyInAnyOrder(ConversationScope.PLANNER, ConversationScope.TRIP);
    }

    @Test
    void onlyATripScopedConversationRequiresATrip() {
        assertThat(ConversationScope.TRIP.requiresTrip()).isTrue();
        assertThat(ConversationScope.PLANNER.requiresTrip())
                .describedAs("the planner home is chat-first: the user talks before any trip exists")
                .isFalse();
        assertThat(Stream.of(ConversationScope.values()).filter(ConversationScope::requiresTrip))
                .containsExactly(ConversationScope.TRIP);
    }
}
