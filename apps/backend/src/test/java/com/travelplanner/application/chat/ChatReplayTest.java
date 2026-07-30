package com.travelplanner.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.chat.ChatTestFakes.ConversationRepositoryFake;
import com.travelplanner.application.chat.ChatTestFakes.ScriptedLlm;
import com.travelplanner.application.chat.ChatTestFakes.TripRepositoryFake;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.model.Conversation;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * ADR 007 resume — gate <b>20C</b>, closing <b>F-39</b>.
 *
 * <h2>What was wrong</h2>
 *
 * <p>The frontend implemented the whole client half of the protocol: it tracks a cursor, sends
 * {@code Last-Event-ID} on reconnect, and drops replayed frames idempotently. The server accepted the
 * header and ignored it, so every reconnect regenerated the turn. That is safe — the
 * {@code client_message_id} idempotency key stops a duplicate question — and it is expensive in three
 * ways that nothing reports as a failure: a second provider call is billed, the user is shown a
 * <em>different</em> answer from the one they had started reading, and history ends up holding an
 * {@code INTERRUPTED} partial and a {@code COMPLETE} answer for one question.
 *
 * <h2>What is now true, and what still is not</h2>
 *
 * <p>Replay is served when the prior turn <strong>finished</strong>. That is the case worth catching —
 * a socket that dies after the model completed but before the browser processed {@code done} — and it
 * costs one query instead of one generation.
 *
 * <p>A turn that was genuinely cut mid-sentence still regenerates, because token deltas are not
 * persisted individually and the server cannot hand back the half-sentence the client is holding. ADR
 * 007's wording was amended to say "persisted messages" rather than "persisted frames", since the
 * original promise was never achievable without storing every delta.
 */
class ChatReplayTest {

    private static final long QUIET_HEARTBEAT_MILLIS = 60_000L;

    private static final String CLIENT_MESSAGE_ID = "cmid-1";

    private final ConversationRepositoryFake repository = new ConversationRepositoryFake();
    private final TripRepositoryFake trips = new TripRepositoryFake();
    private ChatConversationService conversations;
    private ScriptedLlm llm;
    private ChatTurnService service;
    private UserContext caller;

    @BeforeEach
    void setUp() {
        conversations = new ChatConversationService(repository, trips);
        llm = ScriptedLlm.emitting(new LlmEvent.TextDelta("Kyoto in spring."),
                new LlmEvent.Done(StopReason.END_TURN));
        service = new ChatTurnService(conversations, llm, QUIET_HEARTBEAT_MILLIS);
        caller = ChatTestFakes.user(UUID.randomUUID());
    }

    // ------------------------------------------------------------------------------------------
    // The case F-39 is about
    // ------------------------------------------------------------------------------------------

    /** The whole point: a completed turn is replayed, and the model is not called a second time. */
    @Test
    void aReconnectAfterTheTurnFinishedCostsAQueryRatherThanAGeneration() {
        UUID conversationId = completeATurn();
        int callsAfterFirstTurn = llm.prompts().size();

        Optional<ChatReplay> replay = service.replay(sameQuestion(conversationId), caller, null);

        assertThat(replay).isPresent();
        assertThat(llm.prompts())
                .describedAs("a replay must not reach the provider — that is the entire saving")
                .hasSize(callsAfterFirstTurn);
    }

    /** And it is the same answer, not a second one generated from the same question. */
    @Test
    void theReplayedAnswerIsTheStoredOneRatherThanARegeneration() {
        UUID conversationId = completeATurn();
        Message storedAnswer = assistantMessage(conversationId);

        ChatReplay replay = service.replay(sameQuestion(conversationId), caller, null).orElseThrow();

        assertThat(replay.frames()).filteredOn(ChatStreamEvent.MessageStart.class::isInstance)
                .extracting(frame -> ((ChatStreamEvent.MessageStart) frame).content())
                .contains("Kyoto in spring.");
        assertThat(replay.frames()).filteredOn(ChatStreamEvent.MessageStart.class::isInstance)
                .extracting(frame -> ((ChatStreamEvent.MessageStart) frame).messageId())
                .contains(storedAnswer.id());
    }

    /** A replay does not add a third message to a two-message exchange. */
    @Test
    void aReplayWritesNothing() {
        UUID conversationId = completeATurn();
        List<Message> before = repository.messagesOf(conversationId);

        service.replay(sameQuestion(conversationId), caller, null);

        assertThat(repository.messagesOf(conversationId)).isEqualTo(before);
    }

    /**
     * The cursor is honoured: frames the client already applied are not resent.
     *
     * <p>A client that got as far as the user echo needs the answer and not its own message back.
     */
    @Test
    void framesAtOrBelowTheCursorAreNotResent() {
        UUID conversationId = completeATurn();
        Message question = userMessage(conversationId);
        Message answer = assistantMessage(conversationId);

        ChatReplay replay =
                service.replay(sameQuestion(conversationId), caller, question.seq()).orElseThrow();

        assertThat(replay.frames()).filteredOn(ChatStreamEvent.MessageStart.class::isInstance)
                .extracting(frame -> ((ChatStreamEvent.MessageStart) frame).messageId())
                .containsExactly(answer.id());
    }

    /**
     * No cursor means the client kept nothing, so the exchange is replayed from the question.
     *
     * <p>Starting at {@code seq - 1} rather than at the answer: a client that lost even the echo has an
     * optimistic bubble on screen with no server message to reconcile it against, and leaving it
     * orphaned is the duplicate-bubble bug the echo exists to prevent.
     */
    @Test
    void noCursorReplaysTheQuestionTooSoTheOptimisticBubbleStillReconciles() {
        UUID conversationId = completeATurn();

        ChatReplay replay = service.replay(sameQuestion(conversationId), caller, null).orElseThrow();

        assertThat(replay.frames()).filteredOn(ChatStreamEvent.MessageStart.class::isInstance)
                .extracting(frame -> ((ChatStreamEvent.MessageStart) frame).role())
                .containsExactly(ChatMessageRole.USER, ChatMessageRole.ASSISTANT);
        assertThat(replay.frames()).filteredOn(ChatStreamEvent.MessageStart.class::isInstance)
                .extracting(frame -> ((ChatStreamEvent.MessageStart) frame).clientMessageId())
                .containsExactly(CLIENT_MESSAGE_ID, null);
    }

    /** A cursor past the end replays nothing but still terminates the stream. */
    @Test
    void aCursorPastTheEndSendsOnlyTheTerminalFrame() {
        UUID conversationId = completeATurn();

        ChatReplay replay = service.replay(sameQuestion(conversationId), caller, 9_999L).orElseThrow();

        assertThat(replay.frames()).extracting(ChatStreamEvent::eventName).containsExactly("done");
    }

    /** Every replay ends in exactly one terminal frame; a client blocks for ever otherwise. */
    @Test
    void aReplayAlwaysEndsInExactlyOneTerminalFrame() {
        UUID conversationId = completeATurn();

        List<ChatStreamEvent> frames =
                service.replay(sameQuestion(conversationId), caller, null).orElseThrow().frames();

        assertThat(frames).filteredOn(ChatStreamEvent::isTerminal).hasSize(1);
        assertThat(frames.get(frames.size() - 1)).isInstanceOf(ChatStreamEvent.Done.class);
    }

    /**
     * The terminal frame says {@code replay}, not a provider stop reason.
     *
     * <p>{@code end_turn} on a replay would tell a reader — a log, a metric, a future client — that the
     * model had just finished, when nothing was generated and no tokens were spent.
     */
    @Test
    void theTerminalFrameDoesNotClaimAProviderStopReason() {
        UUID conversationId = completeATurn();

        List<ChatStreamEvent> frames =
                service.replay(sameQuestion(conversationId), caller, null).orElseThrow().frames();

        assertThat(((ChatStreamEvent.Done) frames.get(frames.size() - 1)).stopReason())
                .isEqualTo("replay")
                .isNotEqualTo(StopReason.END_TURN.name().toLowerCase(java.util.Locale.ROOT));
    }

    // ------------------------------------------------------------------------------------------
    // When replay must decline
    // ------------------------------------------------------------------------------------------

    /**
     * An interrupted turn regenerates, as before.
     *
     * <p>The honest limit. The client holds a half-sentence assembled from deltas that were never
     * persisted, so there is nothing to resume it from — only to redo. This is why ADR 007's wording had
     * to change rather than simply be implemented.
     */
    @Test
    void anInterruptedTurnIsNotReplayableBecauseDeltasWereNeverPersisted() {
        UUID conversationId = interruptATurn();

        assertThat(service.replay(sameQuestion(conversationId), caller, null)).isEmpty();
    }

    /** A turn still marked {@code STREAMING} is live or died unsettled; either way it is not history. */
    @Test
    void aStreamingTurnIsNotReplayable() {
        ChatTurn turn = service.openTurn(freshQuestion(), caller);

        assertThat(service.replay(sameQuestion(turn.conversationId()), caller, null)).isEmpty();
    }

    /**
     * A question committed with no answer row behind it is not a replay either.
     *
     * <p>The previous attempt died between committing the user message and opening the assistant row.
     * There is nothing to replay, and treating it as one would terminate the stream with {@code done}
     * and no answer — a client left staring at its own message for ever.
     */
    @Test
    void aQuestionWithNoAnswerRowFallsThroughToANormalTurn() {
        UUID conversationId = openConversationWithOnlyAQuestion();

        assertThat(service.replay(sameQuestion(conversationId), caller, null)).isEmpty();
    }

    /** A first send is not a resume, whatever header happens to be on it. */
    @Test
    void aFirstSendIsNotAResumeEvenWithACursor() {
        assertThat(service.replay(freshQuestion(), caller, 7L)).isEmpty();
    }

    /**
     * A different question in the same conversation is a new turn.
     *
     * <p>The reconnect test that would otherwise pass for the wrong reason: replay keys on the
     * {@code clientMessageId}, so a genuinely new message must not be answered from history simply
     * because the thread has some.
     */
    @Test
    void aNewQuestionInAnAnsweredConversationIsNotAReplay() {
        UUID conversationId = completeATurn();

        assertThat(service.replay(new SendChatMessageCommand(ChatTarget.planner(), conversationId,
                "cmid-2", "and food?"), caller, null)).isEmpty();
    }

    /** A resume for a conversation that does not exist creates nothing and replays nothing. */
    @Test
    void aResumeAgainstNoConversationCreatesNothing() {
        assertThat(service.replay(freshQuestion(), caller, 3L)).isEmpty();
        assertThat(repository.messagesOf(UUID.randomUUID())).isEmpty();
    }

    // ------------------------------------------------------------------------------------------

    /** Runs one full turn and returns its conversation id. */
    private UUID completeATurn() {
        ChatTurn turn = service.openTurn(freshQuestion(), caller);
        service.stream(turn).collectList().block(Duration.ofSeconds(10));
        return turn.conversationId();
    }

    /** Runs a turn whose subscriber walks away mid-answer, leaving the row {@code INTERRUPTED}. */
    private UUID interruptATurn() {
        ChatTurnService interruptible = new ChatTurnService(conversations,
                new ScriptedLlm(() -> Flux.<LlmEvent>just(new LlmEvent.TextDelta("Kyoto "))
                        .concatWith(Flux.never())),
                QUIET_HEARTBEAT_MILLIS);
        ChatTurn turn = interruptible.openTurn(freshQuestion(), caller);
        interruptible.stream(turn).take(3).collectList().block(Duration.ofSeconds(10));
        return turn.conversationId();
    }

    /** A conversation holding a committed question and nothing after it. */
    private UUID openConversationWithOnlyAQuestion() {
        Conversation conversation = conversations.resolveForAppend(ChatTarget.planner(), null, caller);
        conversations.appendUserMessage(conversation.id(), CLIENT_MESSAGE_ID, "Kyoto in spring?", caller);
        return conversation.id();
    }

    private SendChatMessageCommand freshQuestion() {
        return new SendChatMessageCommand(ChatTarget.planner(), null, CLIENT_MESSAGE_ID,
                "Kyoto in spring?");
    }

    private SendChatMessageCommand sameQuestion(UUID conversationId) {
        return new SendChatMessageCommand(ChatTarget.planner(), conversationId, CLIENT_MESSAGE_ID,
                "Kyoto in spring?");
    }

    private Message userMessage(UUID conversationId) {
        return messageWithRole(conversationId, ChatMessageRole.USER);
    }

    private Message assistantMessage(UUID conversationId) {
        return messageWithRole(conversationId, ChatMessageRole.ASSISTANT);
    }

    private Message messageWithRole(UUID conversationId, ChatMessageRole role) {
        return repository.messagesOf(conversationId).stream()
                .filter(message -> message.role() == role)
                .findFirst()
                .orElseThrow();
    }
}
