package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class MessageTest {

    private static final Instant NOW = Instant.parse("2026-07-29T09:00:00Z");
    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final String CLIENT_ID = "c1f0a3e4-retry-key";

    // -------------------------------------------------------------------------------------
    // The six factories.
    // -------------------------------------------------------------------------------------

    @Test
    void aUserMessageArrivesWholeAndCarriesItsRetryKey() {
        Message message = Message.fromUser(CONVERSATION, 1L, "Japan in spring", CLIENT_ID, NOW);

        assertThat(message.id()).isNotNull();
        assertThat(message.role()).isEqualTo(ChatMessageRole.USER);
        assertThat(message.status()).isEqualTo(ChatMessageStatus.COMPLETE);
        assertThat(message.content()).isEqualTo("Japan in spring");
        assertThat(message.clientMessageIdIfPresent()).contains(CLIENT_ID);
        assertThat(message.completedAtIfPresent()).contains(NOW);
        assertThat(message.toolCallIdIfPresent()).isEmpty();
    }

    @Test
    void anAssistantMessageOpensEmptyAndStreaming() {
        // Persisted BEFORE the first token so a reload mid-stream is served from the database
        // rather than from a buffer a restart would lose (ADR 007).
        Message message = Message.assistantStreamStarted(CONVERSATION, 2L, NOW);

        assertThat(message.role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(message.status()).isEqualTo(ChatMessageStatus.STREAMING);
        assertThat(message.content()).isEmpty();
        assertThat(message.completedAtIfPresent()).isEmpty();
        assertThat(message.clientMessageIdIfPresent()).isEmpty();
    }

    @Test
    void systemToolAndLifecycleMessagesAreWrittenOnceComplete() {
        Message system = Message.system(CONVERSATION, 3L, "Research finished.", NOW);
        Message call = Message.toolCall(CONVERSATION, 4L, "tu_01", "create_trip", "{}", NOW);
        Message result = Message.toolResult(CONVERSATION, 5L, "tu_01", "{\"trip_id\":\"x\"}", NOW);
        Message event = Message.lifecycleEvent(CONVERSATION, 6L, "{\"type\":\"trip_created\"}", NOW);

        assertThat(system.role()).isEqualTo(ChatMessageRole.SYSTEM);
        assertThat(call.role()).isEqualTo(ChatMessageRole.TOOL_CALL);
        assertThat(call.toolName()).isEqualTo("create_trip");
        assertThat(result.role()).isEqualTo(ChatMessageRole.TOOL_RESULT);
        assertThat(event.role()).isEqualTo(ChatMessageRole.LIFECYCLE_EVENT);
        assertThat(system.status()).isEqualTo(ChatMessageStatus.COMPLETE);
        assertThat(call.status()).isEqualTo(ChatMessageStatus.COMPLETE);
        assertThat(result.status()).isEqualTo(ChatMessageStatus.COMPLETE);
        assertThat(event.status()).isEqualTo(ChatMessageStatus.COMPLETE);
    }

    @Test
    void aToolResultCorrelatesToTheCallThatProducedIt() {
        Message call = Message.toolCall(CONVERSATION, 4L, "tu_01", "create_trip", "{}", NOW);
        Message result = Message.toolResult(CONVERSATION, 5L, "tu_01", "{}", NOW);
        Message unrelated = Message.toolResult(CONVERSATION, 6L, "tu_99", "{}", NOW);

        assertThat(call.correlatesWith(result)).isTrue();
        assertThat(result.correlatesWith(call)).isTrue();
        assertThat(call.correlatesWith(unrelated)).isFalse();
        assertThat(call.correlatesWith(null)).isFalse();
        assertThat(Message.system(CONVERSATION, 7L, "note", NOW).correlatesWith(call))
                .describedAs("a message with no tool call id correlates with nothing")
                .isFalse();
    }

    // -------------------------------------------------------------------------------------
    // Idempotency (tasks/20 Definition of Done).
    // -------------------------------------------------------------------------------------

    @Test
    void aUserMessageWithoutARetryKeyIsRefused() {
        // A user message with no idempotency key is one a dropped response can duplicate, which is
        // exactly what the Definition of Done forbids.
        assertThatThrownBy(() -> Message.fromUser(CONVERSATION, 1L, "hello", null, NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> Message.fromUser(CONVERSATION, 1L, "hello", "   ", NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void theRetryKeyIsTrimmedSoWhitespaceCannotDefeatTheUniqueIndex() {
        // " abc " and "abc" are the same send. Without trimming they would be two distinct values
        // in uq_message_conversation_client_id, and the retry would commit twice.
        Message padded = Message.fromUser(CONVERSATION, 1L, "hello", "  abc  ", NOW);

        assertThat(padded.clientMessageId()).isEqualTo("abc");
        assertThat(padded.hasClientMessageId("abc")).isTrue();
        assertThat(padded.hasClientMessageId("other")).isFalse();
        assertThat(padded.hasClientMessageId(null)).isFalse();
    }

    @Test
    void aResendResolvesToTheSameStoredMessageRatherThanASecondOne() {
        // The domain-level shape of the guarantee: the retry carries the same key, so the lookup
        // that precedes an append finds the committed row and nothing new is written.
        Message committed = Message.fromUser(CONVERSATION, 1L, "Japan in spring", CLIENT_ID, NOW);
        Message retryAttempt = Message.fromUser(CONVERSATION, 2L, "Japan in spring", CLIENT_ID,
                NOW.plusSeconds(5));

        assertThat(committed.hasClientMessageId(retryAttempt.clientMessageId())).isTrue();
        assertThat(committed.id())
                .describedAs("two attempts are distinct objects; only the key makes them one send")
                .isNotEqualTo(retryAttempt.id());
    }

    @Test
    void onlyAUserMessageMayCarryARetryKey() {
        // Mirrors ck_message_client_id_is_user_only. A key on generated text would invite a resume
        // path that re-commits model output under an id the client never issued.
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L,
                ChatMessageRole.ASSISTANT, ChatMessageStatus.COMPLETE, "hi", CLIENT_ID, null, null,
                NOW, NOW, NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void aBlankRetryKeyIsRefusedEvenWhenTheRecordIsBuiltDirectly() {
        // fromUser rejects blanks up front, but the record is public and persistence maps into it.
        // A whitespace-only key stored as-is would be a distinct value in
        // uq_message_conversation_client_id and would let the same send commit twice.
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L,
                ChatMessageRole.USER, ChatMessageStatus.COMPLETE, "hello", "   ", null, null,
                NOW, NOW, NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void theRetryKeyIsBoundedToTheColumnWidth() {
        String tooLong = "x".repeat(Message.MAX_CLIENT_MESSAGE_ID_LENGTH + 1);

        assertThatThrownBy(() -> Message.fromUser(CONVERSATION, 1L, "hello", tooLong, NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(Message.fromUser(CONVERSATION, 1L, "hello",
                "x".repeat(Message.MAX_CLIENT_MESSAGE_ID_LENGTH), NOW).clientMessageId())
                .hasSize(Message.MAX_CLIENT_MESSAGE_ID_LENGTH);
    }

    // -------------------------------------------------------------------------------------
    // Ordering.
    // -------------------------------------------------------------------------------------

    @Test
    void aSequenceNumberBelowOneIsRefused() {
        assertThatThrownBy(() -> Message.system(CONVERSATION, 0L, "x", NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> Message.system(CONVERSATION, -1L, "x", NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void twoMessagesSharingATimestampAreStillTotallyOrdered() {
        // The whole reason seq exists: Postgres fixes now() for a transaction, so every row one
        // turn writes carries an identical created_at. Ordering must not consult it.
        Message call = Message.toolCall(CONVERSATION, 7L, "tu_01", "create_trip", "{}", NOW);
        Message result = Message.toolResult(CONVERSATION, 8L, "tu_01", "{}", NOW);

        assertThat(call.createdAt()).isEqualTo(result.createdAt());
        assertThat(call.seq()).isLessThan(result.seq());
    }

    // -------------------------------------------------------------------------------------
    // Streaming, and the partial-message rule.
    // -------------------------------------------------------------------------------------

    @Test
    void deltasAccumulateIntoTheVisibleContent() {
        Message message = Message.assistantStreamStarted(CONVERSATION, 1L, NOW)
                .appendContent("Tokyo ", NOW.plusSeconds(1))
                .appendContent("in spring", NOW.plusSeconds(2));

        assertThat(message.content()).isEqualTo("Tokyo in spring");
        assertThat(message.status()).isEqualTo(ChatMessageStatus.STREAMING);
        assertThat(message.updatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(message.createdAt()).isEqualTo(NOW);
    }

    @Test
    void aNullDeltaLeavesTheContentUntouched() {
        Message started = Message.assistantStreamStarted(CONVERSATION, 1L, NOW)
                .appendContent("Tokyo", NOW);

        assertThat(started.appendContent(null, NOW.plusSeconds(1)).content()).isEqualTo("Tokyo");
    }

    @Test
    void completingEndsTheMessageAndStampsWhen() {
        Message done = Message.assistantStreamStarted(CONVERSATION, 1L, NOW)
                .appendContent("Tokyo", NOW)
                .complete(NOW.plusSeconds(3));

        assertThat(done.status()).isEqualTo(ChatMessageStatus.COMPLETE);
        assertThat(done.status().isPartial()).isFalse();
        assertThat(done.completedAtIfPresent()).contains(NOW.plusSeconds(3));
        assertThat(done.content()).isEqualTo("Tokyo");
    }

    @Test
    void aDisconnectMarksThePartialMessageRatherThanDiscardingIt() {
        // tasks/20: partial assistant messages must be "explicitly marked or safely discarded";
        // ADR 007 chose marking, because the common mobile case is a transient network loss
        // part-way through a long answer.
        Message cut = Message.assistantStreamStarted(CONVERSATION, 1L, NOW)
                .appendContent("Tokyo in spr", NOW.plusSeconds(2))
                .interrupt(NOW.plusSeconds(3));

        assertThat(cut.status()).isEqualTo(ChatMessageStatus.INTERRUPTED);
        assertThat(cut.status().isPartial()).isTrue();
        assertThat(cut.content())
                .describedAs("the text the user already watched arrive is kept")
                .isEqualTo("Tokyo in spr");
        assertThat(cut.completedAtIfPresent()).contains(NOW.plusSeconds(3));
    }

    @Test
    void aStreamErrorMarksTheMessageFailedAndKeepsWhatArrived() {
        Message failed = Message.assistantStreamStarted(CONVERSATION, 1L, NOW)
                .appendContent("Toky", NOW.plusSeconds(1))
                .fail(NOW.plusSeconds(2));

        assertThat(failed.status()).isEqualTo(ChatMessageStatus.FAILED);
        assertThat(failed.status().isPartial()).isTrue();
        assertThat(failed.content()).isEqualTo("Toky");
    }

    @Test
    void aTerminalMessageRefusesEveryFurtherWrite() {
        // A late frame from a cancelled run must not resurrect text the user was already told was
        // cut short.
        Message cut = Message.assistantStreamStarted(CONVERSATION, 1L, NOW).interrupt(NOW);

        assertThatThrownBy(() -> cut.appendContent("more", NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> cut.complete(NOW)).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> cut.interrupt(NOW)).isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> cut.fail(NOW)).isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void onlyAnAssistantMessageMayBeStreaming() {
        // Stricter than the database, and deliberately so: a SYSTEM row left mid-stream would be a
        // bug with no code path able to finish it.
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L,
                ChatMessageRole.SYSTEM, ChatMessageStatus.STREAMING, "", null, null, null, NOW, NOW, null))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void terminalStatusAndItsTimestampMustAgreeInBothDirections() {
        // Mirrors ck_message_completed_at_matches_status.
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L,
                ChatMessageRole.SYSTEM, ChatMessageStatus.COMPLETE, "x", null, null, null, NOW, NOW, null))
                .describedAs("finished, with no record of when")
                .isInstanceOf(ValidationFailedException.class);

        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L,
                ChatMessageRole.ASSISTANT, ChatMessageStatus.STREAMING, "x", null, null, null, NOW, NOW, NOW))
                .describedAs("still streaming, yet already completed")
                .isInstanceOf(ValidationFailedException.class);
    }

    // -------------------------------------------------------------------------------------
    // Tool correlation invariants.
    // -------------------------------------------------------------------------------------

    @Test
    void toolCorrelationBelongsToExactlyTheTwoToolRoles() {
        // Mirrors ck_message_tool_call_id_paired, an equivalence: an orphan id on an assistant row
        // is refused as firmly as a tool result with nothing to correlate to.
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L,
                ChatMessageRole.ASSISTANT, ChatMessageStatus.COMPLETE, "x", null, "tu_01", null,
                NOW, NOW, NOW))
                .describedAs("an orphan tool call id")
                .isInstanceOf(ValidationFailedException.class);

        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L,
                ChatMessageRole.TOOL_RESULT, ChatMessageStatus.COMPLETE, "x", null, null, null,
                NOW, NOW, NOW))
                .describedAs("a tool result with nothing to correlate to")
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void aToolCallMustNameTheToolItInvoked() {
        assertThatThrownBy(() -> Message.toolCall(CONVERSATION, 1L, "tu_01", null, "{}", NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> Message.toolCall(CONVERSATION, 1L, "tu_01", "  ", "{}", NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void toolIdentifiersAreBoundedToTheColumnWidth() {
        String tooLong = "x".repeat(Message.MAX_TOOL_IDENTIFIER_LENGTH + 1);

        assertThatThrownBy(() -> Message.toolCall(CONVERSATION, 1L, tooLong, "create_trip", "{}", NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> Message.toolCall(CONVERSATION, 1L, "tu_01", tooLong, "{}", NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    // -------------------------------------------------------------------------------------
    // Construction guards.
    // -------------------------------------------------------------------------------------

    @Test
    void nullContentBecomesEmptyRatherThanPropagating() {
        // Matches the column's NOT NULL DEFAULT ''. A streaming row legitimately has no text yet,
        // and "" says that far more usefully than a null every reader must guard.
        assertThat(Message.system(CONVERSATION, 1L, null, NOW).content()).isEmpty();
    }

    @Test
    void everyIdentifyingFieldIsRequired() {
        assertThatThrownBy(() -> new Message(null, CONVERSATION, 1L, ChatMessageRole.SYSTEM,
                ChatMessageStatus.COMPLETE, "x", null, null, null, NOW, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), null, 1L, ChatMessageRole.SYSTEM,
                ChatMessageStatus.COMPLETE, "x", null, null, null, NOW, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L, null,
                ChatMessageStatus.COMPLETE, "x", null, null, null, NOW, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L, ChatMessageRole.SYSTEM,
                null, "x", null, null, null, NOW, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L, ChatMessageRole.SYSTEM,
                ChatMessageStatus.COMPLETE, "x", null, null, null, null, NOW, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Message(UUID.randomUUID(), CONVERSATION, 1L, ChatMessageRole.SYSTEM,
                ChatMessageStatus.COMPLETE, "x", null, null, null, NOW, null, NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
