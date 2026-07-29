package com.travelplanner.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.application.chat.ChatTestFakes.ConversationRepositoryFake;
import com.travelplanner.application.chat.ChatTestFakes.ScriptedLlm;
import com.travelplanner.application.chat.ChatTestFakes.TripRepositoryFake;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.MessageRole;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import com.travelplanner.domain.exception.AiProviderException;
import com.travelplanner.domain.model.Message;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * The streaming half of a chat turn.
 *
 * <p>Reactor's {@code StepVerifier} rather than a servlet: the properties worth pinning — the frame
 * order, what a cancellation leaves behind, what a provider failure turns into — are properties of
 * the {@code Flux}, and asserting them through MockMvc would mean asserting them through an async
 * dispatch that can only report what a container happened to flush.
 */
class ChatTurnServiceTest {

    /** Long enough that no heartbeat interleaves with a test that is not about heartbeats. */
    private static final long QUIET_HEARTBEAT_MILLIS = 60_000L;

    private final ConversationRepositoryFake repository = new ConversationRepositoryFake();
    private final TripRepositoryFake trips = new TripRepositoryFake();
    private ChatConversationService conversations;
    private UserContext caller;

    @BeforeEach
    void setUp() {
        conversations = new ChatConversationService(repository, trips);
        caller = ChatTestFakes.user(UUID.randomUUID());
    }

    // ------------------------------------------------------------------------------------------
    // Frame order and content.
    // ------------------------------------------------------------------------------------------

    @Test
    void aTurnEmitsTheUserEchoTheAssistantOpeningTheDeltasAndExactlyOneTerminalFrame() {
        ChatTurnService service = serviceEmitting(
                new LlmEvent.TextDelta("Kyoto "),
                new LlmEvent.TextDelta("in spring"),
                new LlmEvent.Usage(10, 4, 0),
                new LlmEvent.Done(StopReason.END_TURN));

        List<ChatStreamEvent> frames = collect(service);

        assertThat(frames).extracting(ChatStreamEvent::eventName)
                .containsExactly("message_start", "message_start", "text_delta", "text_delta",
                        "usage", "message_end", "done");
    }

    @Test
    void theUserEchoCarriesTheClientMessageIdSoTheOptimisticBubbleIsNotDuplicated() {
        // STATUS F-36 item 3. This field is the entire reconciliation channel: without it the echo
        // appends a second copy of the message the user is already looking at.
        ChatTurnService service = serviceEmitting(new LlmEvent.Done(StopReason.END_TURN));

        ChatStreamEvent.MessageStart echo = (ChatStreamEvent.MessageStart) collect(service).get(0);

        assertThat(echo.role()).isEqualTo(ChatMessageRole.USER);
        assertThat(echo.clientMessageId()).isEqualTo("cmid-1");
        assertThat(echo.content()).isEqualTo("Kyoto in spring?");
    }

    @Test
    void theAssistantOpeningCarriesNoClientMessageIdAndNoContent() {
        // Only a client-authored message has a retry key (ck_message_client_id_is_user_only), and an
        // empty string would be indistinguishable from "the model said nothing".
        ChatTurnService service = serviceEmitting(new LlmEvent.Done(StopReason.END_TURN));

        ChatStreamEvent.MessageStart opening = (ChatStreamEvent.MessageStart) collect(service).get(1);

        assertThat(opening.role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(opening.clientMessageId()).isNull();
        assertThat(opening.content()).isNull();
    }

    @Test
    void frameIdsAppearOnlyOnPersistedPositionsAndIncrease() {
        ChatTurnService service = serviceEmitting(
                new LlmEvent.TextDelta("one"),
                new LlmEvent.TextDelta("two"),
                new LlmEvent.Done(StopReason.END_TURN));

        List<ChatStreamEvent> frames = collect(service);

        assertThat(frames.get(0).frameId()).isEqualTo(1L);
        assertThat(frames.get(1).frameId()).isEqualTo(2L);
        // Deltas carry none: repeating the message's seq would make the client's replay filter drop
        // every token after the first.
        assertThat(frames.get(2).frameId()).isNull();
        assertThat(frames.get(3).frameId()).isNull();
        assertThat(frames.get(4).frameId()).isEqualTo(2L);
    }

    @Test
    void anEmptyDeltaIsNotForwarded() {
        ChatTurnService service = serviceEmitting(
                new LlmEvent.TextDelta(""),
                new LlmEvent.TextDelta("real"),
                new LlmEvent.Done(StopReason.END_TURN));

        assertThat(collect(service)).filteredOn(event -> event instanceof ChatStreamEvent.TextDelta)
                .hasSize(1);
    }

    // ------------------------------------------------------------------------------------------
    // Persistence, and the transaction boundary it proves.
    // ------------------------------------------------------------------------------------------

    @Test
    void everyWriteTheTurnNeedsIsCommittedBeforeTheStreamIsSubscribed() {
        // The property tasks/20 forbids breaking: no transaction may still be open once tokens
        // start flowing. `openTurn` returning with both rows already committed is what makes the
        // streaming path connection-free.
        ChatTurnService service = serviceEmitting(new LlmEvent.Done(StopReason.END_TURN));
        ChatTurn turn = service.openTurn(command(), caller);

        assertThat(repository.message(turn.userMessage().id())).isPresent();
        assertThat(repository.message(turn.assistantMessage().id())).isPresent();
        assertThat(repository.message(turn.assistantMessage().id()).orElseThrow().status())
                .isEqualTo(ChatMessageStatus.STREAMING);
        assertThat(repository.saveMessageCalls()).isZero();
    }

    @Test
    void aCompletedTurnPersistsTheWholeAnswerExactlyOnce() {
        ChatTurnService service = serviceEmitting(
                new LlmEvent.TextDelta("Kyoto "),
                new LlmEvent.TextDelta("in spring"),
                new LlmEvent.Done(StopReason.END_TURN));
        ChatTurn turn = service.openTurn(command(), caller);

        StepVerifier.create(service.stream(turn)).expectNextCount(6).verifyComplete();

        Message stored = repository.message(turn.assistantMessage().id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ChatMessageStatus.COMPLETE);
        assertThat(stored.content()).isEqualTo("Kyoto in spring");
        assertThat(repository.saveMessageCalls()).isEqualTo(1);
    }

    @Test
    void aCancelledSubscriptionLeavesThePartialAnswerInterruptedRatherThanCompleteOrDiscarded() {
        // The disconnect case. ADR 007 keeps the text and labels it; the row must never read
        // COMPLETE, because the UI would then present a truncated answer as a finished one.
        ChatTurnService service = serviceStreaming(() -> Flux.concat(
                Flux.just(new LlmEvent.TextDelta("Kyoto in")),
                Flux.never()));
        ChatTurn turn = service.openTurn(command(), caller);

        StepVerifier.create(service.stream(turn))
                .expectNextCount(3)
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        Message stored = repository.message(turn.assistantMessage().id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ChatMessageStatus.INTERRUPTED);
        assertThat(stored.content()).isEqualTo("Kyoto in");
    }

    @Test
    void aClientThatDisconnectsBeforeAnyTokenStillSettlesTheRow() {
        // The row is opened before the model is called, so a cancellation during the opening frames
        // must still settle it — otherwise it stays STREAMING for ever and no reload can render it.
        ChatTurnService service = serviceStreaming(Flux::never);
        ChatTurn turn = service.openTurn(command(), caller);

        StepVerifier.create(service.stream(turn)).thenCancel().verify(Duration.ofSeconds(5));

        assertThat(repository.message(turn.assistantMessage().id()).orElseThrow().status())
                .isEqualTo(ChatMessageStatus.INTERRUPTED);
    }

    @Test
    void aFinishedTurnIsNotThenMarkedInterruptedByItsOwnCancellation() {
        // `takeUntil` cancels the source immediately after the terminal frame, so the cancellation
        // hook fires on the happy path too. Settlement has to be once-only or every completed
        // answer would be relabelled as cut short.
        ChatTurnService service = serviceEmitting(
                new LlmEvent.TextDelta("done"),
                new LlmEvent.Done(StopReason.END_TURN));
        ChatTurn turn = service.openTurn(command(), caller);

        StepVerifier.create(service.stream(turn)).expectNextCount(5).verifyComplete();

        assertThat(repository.message(turn.assistantMessage().id()).orElseThrow().status())
                .isEqualTo(ChatMessageStatus.COMPLETE);
        assertThat(repository.saveMessageCalls()).isEqualTo(1);
    }

    @Test
    void aProviderThatEndsWithoutAStopReasonStillProducesATerminalFrame() {
        // A stream that merely stops is indistinguishable from a dropped body at the client.
        ChatTurnService service = serviceEmitting(new LlmEvent.TextDelta("partial"));

        List<ChatStreamEvent> frames = collect(service);

        assertThat(frames).last().isInstanceOf(ChatStreamEvent.Done.class);
        assertThat(frames).extracting(ChatStreamEvent::eventName)
                .containsExactly("message_start", "message_start", "text_delta", "message_end", "done");
    }

    // ------------------------------------------------------------------------------------------
    // Failures.
    // ------------------------------------------------------------------------------------------

    @Test
    void aProviderStreamErrorBecomesAnErrorFrameAndAFailedRow() {
        ChatTurnService service = serviceEmitting(
                new LlmEvent.TextDelta("half an "),
                new LlmEvent.StreamError(AiProviderException.UNAVAILABLE, "anthropic 529", Map.of()));
        ChatTurn turn = service.openTurn(command(), caller);
        List<ChatStreamEvent> frames = drain(service.stream(turn));

        ChatStreamEvent.StreamError error = (ChatStreamEvent.StreamError) frames.get(frames.size() - 1);
        assertThat(error.code()).isEqualTo("ai_unavailable");
        Message stored = repository.message(turn.assistantMessage().id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(ChatMessageStatus.FAILED);
        assertThat(stored.content()).isEqualTo("half an ");
    }

    @Test
    void theProvidersOwnErrorTextNeverReachesTheWire() {
        // §6.1's `message` is a developer string the frontend never renders — it resolves
        // `common.errors.<code>` — so provider text on this frame would be leakage with no reader.
        ChatTurnService service = serviceEmitting(new LlmEvent.StreamError(
                AiProviderException.TIMEOUT, "upstream said: internal reasoning trace 0xdeadbeef",
                Map.of("provider_payload", "secret")));

        ChatStreamEvent.StreamError error = (ChatStreamEvent.StreamError) last(collect(service));

        assertThat(error.message()).doesNotContain("reasoning").doesNotContain("0xdeadbeef");
        assertThat(error.code()).isEqualTo("ai_timeout");
    }

    @Test
    void anUnregisteredErrorCodeIsDowngradedRatherThanPublished() {
        // An unregistered code renders to the user as a raw identifier (PLAN §6.1).
        ChatTurnService service = serviceEmitting(
                new LlmEvent.StreamError("provider_specific_nonsense", "boom", Map.of()));

        ChatStreamEvent.StreamError error = (ChatStreamEvent.StreamError) last(collect(service));

        assertThat(error.code()).isEqualTo("internal_error");
    }

    @Test
    void aFluxThatFailsOutrightBecomesAnErrorFrameAndNotABareAbort() {
        // ADR 007: "never a bare stream abort". A client cannot tell a failed model from a failed
        // network unless the server says which it was, and only one of them is worth auto-retrying.
        ChatTurnService service = serviceStreaming(() ->
                Flux.error(AiProviderException.rateLimited("quota")));
        ChatTurn turn = service.openTurn(command(), caller);

        List<ChatStreamEvent> frames = drain(service.stream(turn));

        assertThat(last(frames)).isInstanceOf(ChatStreamEvent.StreamError.class);
        assertThat(((ChatStreamEvent.StreamError) last(frames)).code()).isEqualTo("ai_rate_limited");
        assertThat(repository.message(turn.assistantMessage().id()).orElseThrow().status())
                .isEqualTo(ChatMessageStatus.FAILED);
    }

    @Test
    void anUnexpectedFailureIsAnInternalErrorFrame() {
        ChatTurnService service = serviceStreaming(() -> Flux.error(new IllegalStateException("boom")));

        ChatStreamEvent.StreamError error = (ChatStreamEvent.StreamError) last(collect(service));

        assertThat(error.code()).isEqualTo("internal_error");
    }

    // ------------------------------------------------------------------------------------------
    // The allow-list. tasks/20: "do not expose internal reasoning or raw provider events".
    // ------------------------------------------------------------------------------------------

    @Test
    void aDomainEventThatIsNotTripCreatedIsDroppedRatherThanForwarded() {
        // `DomainEvent` is the one variant carrying a free-form map, so it is the one that could
        // otherwise carry a provider's reasoning trace to a browser.
        ChatTurnService service = serviceEmitting(
                new LlmEvent.DomainEvent("reasoning", Map.of("thinking", "step 1: the user wants…")),
                new LlmEvent.Done(StopReason.END_TURN));

        List<ChatStreamEvent> frames = collect(service);

        assertThat(frames).noneMatch(event -> event instanceof ChatStreamEvent.TripCreated);
        assertThat(frames).extracting(ChatStreamEvent::eventName)
                .containsExactly("message_start", "message_start", "message_end", "done");
    }

    @Test
    void aTripCreatedDomainEventWithNoTripIdIsDropped() {
        ChatTurnService service = serviceEmitting(
                new LlmEvent.DomainEvent("trip_created", Map.of()),
                new LlmEvent.Done(StopReason.END_TURN));

        assertThat(collect(service)).noneMatch(event -> event instanceof ChatStreamEvent.TripCreated);
    }

    @Test
    void aWellFormedTripCreatedDomainEventIsForwarded() {
        // Representable now, emitted by nothing until tasks 21/22 — the frame exists so the handoff
        // does not need a contract change later.
        UUID tripId = UUID.randomUUID();
        ChatTurnService service = serviceEmitting(
                new LlmEvent.DomainEvent("trip_created", Map.of("trip_id", tripId.toString())),
                new LlmEvent.Done(StopReason.END_TURN));

        assertThat(collect(service)).contains(new ChatStreamEvent.TripCreated(tripId));
    }

    @Test
    void toolLifecycleEventsAreRepresentableEvenThoughNothingEmitsThemYet() {
        ChatTurnService service = serviceEmitting(
                new LlmEvent.ToolUseStart("call-1", "search"),
                new LlmEvent.ToolInputDelta("call-1", "{\"q\":"),
                new LlmEvent.ToolUseEnd("call-1"),
                new LlmEvent.ToolResult("call-1", "{}"),
                new LlmEvent.Done(StopReason.END_TURN));

        assertThat(collect(service)).extracting(ChatStreamEvent::eventName)
                .containsExactly("message_start", "message_start", "tool_use_start", "tool_input_delta",
                        "tool_use_end", "tool_result", "message_end", "done");
    }

    // ------------------------------------------------------------------------------------------
    // Heartbeat and prompt assembly.
    // ------------------------------------------------------------------------------------------

    @Test
    void anIdleStreamStillTicksSoAProxyDoesNotReapIt() {
        ScriptedLlm llm = new ScriptedLlm(Flux::never);
        ChatTurnService service = new ChatTurnService(conversations, llm, 20L);
        ChatTurn turn = service.openTurn(command(), caller);

        StepVerifier.create(service.stream(turn))
                .expectNextCount(2)
                .expectNextMatches(event -> event instanceof ChatStreamEvent.Heartbeat)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void thePromptCarriesTheStoredHistoryAsProseAndNothingElse() {
        ScriptedLlm llm = ScriptedLlm.emitting(new LlmEvent.Done(StopReason.END_TURN));
        ChatTurnService service = new ChatTurnService(conversations, llm, QUIET_HEARTBEAT_MILLIS);
        ChatTurn first = service.openTurn(command(), caller);
        drain(service.stream(first));

        ChatTurn second = service.openTurn(
                new SendChatMessageCommand(ChatTarget.planner(), first.conversationId(), "cmid-2", "and food?"),
                caller);
        drain(service.stream(second));

        List<PromptMessage> messages = llm.prompts().get(1).messages();
        assertThat(messages.get(0).role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(messages).extracting(PromptMessage::text).contains("Kyoto in spring?", "and food?");
        // The empty assistant row of the second turn must not be in its own prompt.
        assertThat(messages).allMatch(message -> !message.text().isBlank());
    }

    // ------------------------------------------------------------------------------------------

    private ChatTurnService serviceEmitting(LlmEvent... events) {
        return new ChatTurnService(conversations, ScriptedLlm.emitting(events), QUIET_HEARTBEAT_MILLIS);
    }

    private ChatTurnService serviceStreaming(java.util.function.Supplier<Flux<LlmEvent>> script) {
        return new ChatTurnService(conversations, new ScriptedLlm(script), QUIET_HEARTBEAT_MILLIS);
    }

    private List<ChatStreamEvent> collect(ChatTurnService service) {
        return drain(service.stream(service.openTurn(command(), caller)));
    }

    private static List<ChatStreamEvent> drain(Flux<ChatStreamEvent> stream) {
        return stream.collectList().block(Duration.ofSeconds(10));
    }

    private static ChatStreamEvent last(List<ChatStreamEvent> frames) {
        return frames.get(frames.size() - 1);
    }

    private SendChatMessageCommand command() {
        return new SendChatMessageCommand(ChatTarget.planner(), null, "cmid-1", "Kyoto in spring?");
    }
}
