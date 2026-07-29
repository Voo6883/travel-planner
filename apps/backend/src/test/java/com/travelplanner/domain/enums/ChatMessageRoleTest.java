package com.travelplanner.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class ChatMessageRoleTest {

    @Test
    void coversTheWholeVocabularyTaskTwentyRequires() {
        // tasks/20: "roles/types sufficient for user, assistant, system metadata, tool calls/
        // results, and lifecycle events". Asserted as a set rather than a count so that removing
        // one and adding another cannot pass.
        assertThat(ChatMessageRole.values()).containsExactlyInAnyOrder(
                ChatMessageRole.USER,
                ChatMessageRole.ASSISTANT,
                ChatMessageRole.SYSTEM,
                ChatMessageRole.TOOL_CALL,
                ChatMessageRole.TOOL_RESULT,
                ChatMessageRole.LIFECYCLE_EVENT);
    }

    @Test
    void hasNoRoleUnderWhichChainOfThoughtCouldBeStored() {
        // The tasks/20 Definition of Done — "No hidden chain-of-thought is stored or returned" —
        // as an executable rule. ck_message_role lists exactly these names, so a reasoning trace
        // has no role it could be persisted under; this test is what makes adding one a red build
        // rather than a review comment somebody might miss.
        assertThat(Stream.of(ChatMessageRole.values()).map(role -> role.name().toLowerCase(Locale.ROOT)))
                .noneMatch(name -> name.contains("reason")
                        || name.contains("think")
                        || name.contains("scratch")
                        || name.contains("internal"));
    }

    @Test
    void onlyAUserMessageCarriesARetryKey() {
        assertThat(ChatMessageRole.USER.acceptsClientMessageId()).isTrue();
        assertThat(Stream.of(ChatMessageRole.values())
                .filter(role -> role != ChatMessageRole.USER)
                .filter(ChatMessageRole::acceptsClientMessageId))
                .describedAs("a server-authored message cannot be retried by a client")
                .isEmpty();
    }

    @Test
    void exactlyTheTwoToolRolesCarryCorrelation() {
        assertThat(Stream.of(ChatMessageRole.values()).filter(ChatMessageRole::carriesToolCorrelation))
                .containsExactlyInAnyOrder(ChatMessageRole.TOOL_CALL, ChatMessageRole.TOOL_RESULT);
    }

    @Test
    void onlyAToolCallMustNameItsTool() {
        assertThat(Stream.of(ChatMessageRole.values()).filter(ChatMessageRole::requiresToolName))
                .containsExactly(ChatMessageRole.TOOL_CALL);
    }

    @Test
    void onlyAnAssistantMessageStreams() {
        assertThat(Stream.of(ChatMessageRole.values()).filter(ChatMessageRole::isStreamable))
                .describedAs("everything else is written once, complete, so nothing else can be cut short")
                .containsExactly(ChatMessageRole.ASSISTANT);
    }
}
