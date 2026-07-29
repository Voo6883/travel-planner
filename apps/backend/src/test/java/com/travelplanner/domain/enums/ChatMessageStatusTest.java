package com.travelplanner.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class ChatMessageStatusTest {

    @Test
    void listsExactlyTheFourOutcomesAdrSevenNames() {
        assertThat(ChatMessageStatus.values()).containsExactlyInAnyOrder(
                ChatMessageStatus.STREAMING,
                ChatMessageStatus.COMPLETE,
                ChatMessageStatus.INTERRUPTED,
                ChatMessageStatus.FAILED);
    }

    @Test
    void streamingIsTheOnlyNonTerminalStatus() {
        assertThat(ChatMessageStatus.STREAMING.isTerminal()).isFalse();
        assertThat(Stream.of(ChatMessageStatus.values()).filter(status -> !status.isTerminal()))
                .containsExactly(ChatMessageStatus.STREAMING);
    }

    @Test
    void partialAndTerminalAreDifferentQuestions() {
        // A COMPLETE message is terminal AND whole; an INTERRUPTED one is terminal and NOT. Only a
        // caller that can tell them apart can render the second honestly, which is the whole point
        // of tasks/20's "partial assistant messages must be explicitly marked".
        assertThat(ChatMessageStatus.COMPLETE.isTerminal()).isTrue();
        assertThat(ChatMessageStatus.COMPLETE.isPartial()).isFalse();

        assertThat(ChatMessageStatus.INTERRUPTED.isTerminal()).isTrue();
        assertThat(ChatMessageStatus.INTERRUPTED.isPartial()).isTrue();

        assertThat(ChatMessageStatus.FAILED.isTerminal()).isTrue();
        assertThat(ChatMessageStatus.FAILED.isPartial()).isTrue();

        assertThat(ChatMessageStatus.STREAMING.isPartial())
                .describedAs("an unfinished message is not yet a partial one — it may still complete")
                .isFalse();
    }
}
