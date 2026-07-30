package com.travelplanner.ai.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.ai.client.LlmProvider;
import com.travelplanner.ai.observability.PromptHasher;
import com.travelplanner.domain.ai.LlmCompletion;
import com.travelplanner.domain.ai.LlmEvent;
import com.travelplanner.domain.ai.LlmOptions;
import com.travelplanner.domain.ai.LlmToolCall;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.ai.StopReason;
import com.travelplanner.domain.ai.ToolSpec;
import com.travelplanner.domain.exception.AiProviderException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Record, then replay (review §6.I).
 *
 * <p>The whole mechanism's value rests on one claim: what comes out of the replay adapter is
 * indistinguishable from what the recorded provider returned. So the central tests here record from a
 * scripted "provider", replay through a second store built from the same directory, and compare — a
 * round trip through JSON and back, which is the only version of the claim worth asserting. Testing the
 * two halves separately would let a field the recorder writes and the replayer ignores pass unnoticed,
 * and that failure mode replays a subtly different stream.
 *
 * <p>The refusal tests matter as much. A replay adapter that fell back to the stub on a miss would keep
 * every suite green while quietly testing nothing, which is worse than a failure — so "no fixture" and
 * "wrong half" both have to be loud, and both are asserted.
 */
class ProviderReplayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------------------------------------------------------------------------------------
    // Round trip
    // ---------------------------------------------------------------------------------------

    @Test
    void replaysACompletionExactlyAsTheProviderReturnedIt(@TempDir Path fixtures) {
        Prompt prompt = prompt("Two weeks in Tokyo");
        LlmCompletion original = new LlmCompletion("Try Kyoto in April.",
                List.of(new LlmToolCall("call_1", "create_trip", "{\"name\":\"Japan\"}")),
                new LlmEvent.Usage(410, 92, 128), StopReason.TOOL_USE);

        recordFrom(fixtures, ScriptedProvider.returning(original)).completeWithTools(prompt,
                List.of(), LlmOptions.defaults());
        LlmCompletion replayed = replayFrom(fixtures).completeWithTools(prompt, List.of(),
                LlmOptions.defaults());

        // Everything, including the tool call's raw argument JSON and the cached-token count. A replay
        // that lost `cachedTokens` would silently overstate cost by roughly the cache discount.
        assertThat(replayed).isEqualTo(original);
    }

    @Test
    void replaysAStreamEventForEventInTheSameOrder(@TempDir Path fixtures) {
        Prompt prompt = prompt("Plan Osaka");
        List<LlmEvent> original = List.of(
                new LlmEvent.TextDelta("Osaka "),
                new LlmEvent.TextDelta("is lively."),
                new LlmEvent.ToolUseStart("call_9", "search_pois"),
                new LlmEvent.ToolInputDelta("call_9", "{\"area\":"),
                new LlmEvent.ToolInputDelta("call_9", "\"Namba\"}"),
                new LlmEvent.ToolUseEnd("call_9"),
                new LlmEvent.ToolResult("call_9", "{\"count\":12}"),
                new LlmEvent.Usage(120, 34, 0),
                new LlmEvent.Done(StopReason.TOOL_USE));

        recordFrom(fixtures, ScriptedProvider.streaming(original))
                .stream(prompt, LlmOptions.defaults()).blockLast();

        // The order is the assertion. A tool-call sequence replayed out of order — arguments before the
        // start frame — is a stream no provider produces, and a consumer written against it would be
        // written against a fiction.
        StepVerifier.create(replayFrom(fixtures).stream(prompt, LlmOptions.defaults()))
                .expectNextSequence(original)
                .verifyComplete();
    }

    @Test
    void recordsAPartialStreamWhenTheSubscriberWalksAway(@TempDir Path fixtures) {
        // A cancelled turn is one of the most useful recordings there is: ADR 007's INTERRUPTED status
        // exists for it, and it is awkward to fabricate by hand.
        Prompt prompt = prompt("Plan Nara");
        LlmProvider recorder = recordFrom(fixtures, ScriptedProvider.streaming(List.of(
                new LlmEvent.TextDelta("Nara "),
                new LlmEvent.TextDelta("has deer."),
                new LlmEvent.Usage(50, 10, 0),
                new LlmEvent.Done(StopReason.END_TURN))));

        StepVerifier.create(recorder.stream(prompt, LlmOptions.defaults()), 1)
                .expectNext(new LlmEvent.TextDelta("Nara "))
                .thenCancel()
                .verify();

        StepVerifier.create(replayFrom(fixtures).stream(prompt, LlmOptions.defaults()))
                .expectNext(new LlmEvent.TextDelta("Nara "))
                .verifyComplete();
    }

    // ---------------------------------------------------------------------------------------
    // Redaction
    // ---------------------------------------------------------------------------------------

    @Test
    void neverWritesThePromptToDisk(@TempDir Path fixtures) throws IOException {
        // The property that makes a fixture committable. A recording containing a traveller's message
        // is a file that must not be shared, and a fixture that cannot be shared is not a fixture — it
        // passes on one machine and fails everywhere else.
        Prompt prompt = prompt("Two weeks in Tokyo with my partner, budget 4000 USD");

        recordFrom(fixtures, ScriptedProvider.returning(LlmCompletion.ofText("Noted.",
                LlmEvent.Usage.none()))).complete(prompt, LlmOptions.defaults());

        String written = Files.readString(onlyFixture(fixtures));
        assertThat(written)
                .doesNotContain("Tokyo")
                .doesNotContain("partner")
                .doesNotContain("4000");
        assertThat(written).contains(PromptHasher.hash(prompt));
    }

    @Test
    void namesTheFixtureForThePromptHashSoARenameCannotGoUnnoticed(@TempDir Path fixtures)
            throws IOException {
        Prompt prompt = prompt("Plan Kyoto");
        recordFrom(fixtures, ScriptedProvider.returning(LlmCompletion.ofText("Sure.",
                LlmEvent.Usage.none()))).complete(prompt, LlmOptions.defaults());

        Path fixture = onlyFixture(fixtures);
        assertThat(fixture.getFileName().toString()).isEqualTo(PromptHasher.hash(prompt) + ".json");

        // A hand-renamed fixture would answer for a prompt it was not recorded against, which is worse
        // than a missing one: the test passes, against the wrong recording.
        Files.move(fixture, fixtures.resolve("deadbeef.json"));
        assertThatThrownBy(() -> new RecordedExchangeStore(fixtures, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match its filename");
    }

    // ---------------------------------------------------------------------------------------
    // Refusals — the reason this is trustworthy at all
    // ---------------------------------------------------------------------------------------

    @Test
    void refusesAnUnrecordedPromptRatherThanFallingBackToTheStub(@TempDir Path fixtures) {
        // THE decision. A silent fallback means a test whose prompt changed keeps passing against stub
        // output that asserts nothing about the provider, and the fixture it was meant to exercise is
        // quietly dead. Green, and testing nothing.
        assertThatThrownBy(() -> replayFrom(fixtures).complete(prompt("never recorded"),
                LlmOptions.defaults()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("no recorded exchange")
                // The message has to say how to make the fixture, not merely that one is absent.
                .hasMessageContaining("replay.record=true");
    }

    @Test
    void refusesToServeACompletionRecordingAsAStream(@TempDir Path fixtures) {
        Prompt prompt = prompt("Plan Sapporo");
        recordFrom(fixtures, ScriptedProvider.returning(LlmCompletion.ofText("Cold.",
                LlmEvent.Usage.none()))).complete(prompt, LlmOptions.defaults());

        // Synthesising an event sequence from a completion would fabricate a stream no provider
        // produced — and a consumer would then be tested against timing and framing that do not exist.
        StepVerifier.create(replayFrom(fixtures).stream(prompt, LlmOptions.defaults()))
                .expectErrorSatisfies(failure -> assertThat(failure)
                        .isInstanceOf(AiProviderException.class)
                        .hasMessageContaining("has no a stream half"))
                .verify();
    }

    @Test
    void refusesToRecordADomainEventBecauseNoProviderEmitsOne(@TempDir Path fixtures) {
        // ADR 007: the orchestrator emits DomainEvent after a tool commits; an adapter never does. A
        // recording containing one would describe an impossible stream, and replaying it would let an
        // orchestrator bug pass its own test.
        LlmProvider recorder = recordFrom(fixtures, ScriptedProvider.streaming(List.of(
                new LlmEvent.DomainEvent("trip_created", java.util.Map.of("tripId", "t1")))));

        StepVerifier.create(recorder.stream(prompt("Plan Kobe"), LlmOptions.defaults()))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void refusesAFixtureWithAnUnknownEventTypeInsteadOfSkippingIt(@TempDir Path fixtures)
            throws IOException {
        // A skipped event replays as a stream that is missing something, and the two most valuable
        // recordings — a usage frame and an error frame — are exactly the ones whose absence looks
        // like success. That is the defect this repository already had once, in the router.
        Files.createDirectories(fixtures);
        Files.writeString(fixtures.resolve("abc123.json"), """
                {
                  "promptHash": "abc123",
                  "provider": "anthropic",
                  "model": "claude-sonnet-4-5",
                  "events": [ { "type": "reasoning_delta", "text": "hidden" } ]
                }
                """);

        RecordedExchange exchange = new RecordedExchangeStore(fixtures, objectMapper)
                .find("abc123").orElseThrow();

        assertThatThrownBy(() -> RecordedEventMapper.toDomain(exchange.events().get(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown recorded event type 'reasoning_delta'");
    }

    @Test
    void refusesARecordingWithNeitherHalf() {
        assertThatThrownBy(() -> new RecordedExchange("abc", "anthropic", "m", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neither a completion nor any events");
    }

    // ---------------------------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------------------------

    @Test
    void reportsItselfRatherThanBorrowingARealModelName(@TempDir Path fixtures) {
        // A real model name here would put a live provider's price into ai_call_log for a call that
        // cost nothing, and a cost dashboard containing fictional money is worse than an empty one.
        LlmProvider replay = replayFrom(fixtures);

        assertThat(replay.providerName()).isEqualTo("replay");
        assertThat(replay.modelName()).isEqualTo("replay");
    }

    @Test
    void aRecorderIsTransparentAboutWhichProviderItWraps(@TempDir Path fixtures) {
        LlmProvider recorder = recordFrom(fixtures,
                ScriptedProvider.returning(LlmCompletion.ofText("x", LlmEvent.Usage.none())));

        // The recorder must not change what the router sees, or ai_call_log would attribute recorded
        // traffic to a provider called "recorder".
        assertThat(recorder.providerName()).isEqualTo("scripted");
        assertThat(recorder.modelName()).isEqualTo("scripted-model");
    }

    // ---------------------------------------------------------------------------------------

    private static Prompt prompt(String text) {
        return Prompt.adHoc(List.of(PromptMessage.user(text)));
    }

    private LlmProvider recordFrom(Path fixtures, LlmProvider delegate) {
        return new ProviderRecorder(delegate, new RecordedExchangeStore(fixtures, objectMapper));
    }

    /** A second store over the same directory, so every replay assertion goes through JSON on disk. */
    private LlmProvider replayFrom(Path fixtures) {
        return new ReplayLlmAdapter(new RecordedExchangeStore(fixtures, objectMapper));
    }

    private static Path onlyFixture(Path fixtures) throws IOException {
        try (var files = Files.list(fixtures)) {
            List<Path> found = files.filter(path -> path.toString().endsWith(".json")).toList();
            assertThat(found).hasSize(1);
            return found.get(0);
        }
    }

    /** A provider double: no network, and output the test chose. */
    private record ScriptedProvider(LlmCompletion completion, List<LlmEvent> events)
            implements LlmProvider {

        static ScriptedProvider returning(LlmCompletion completion) {
            return new ScriptedProvider(completion, List.of());
        }

        static ScriptedProvider streaming(List<LlmEvent> events) {
            return new ScriptedProvider(null, events);
        }

        @Override
        public String providerName() {
            return "scripted";
        }

        @Override
        public String modelName() {
            return "scripted-model";
        }

        @Override
        public String complete(Prompt prompt, LlmOptions options) {
            return completeWithTools(prompt, List.of(), options).text();
        }

        @Override
        public LlmCompletion completeWithTools(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
            return completion;
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, LlmOptions options) {
            return stream(prompt, List.of(), options);
        }

        @Override
        public Flux<LlmEvent> stream(Prompt prompt, List<ToolSpec> tools, LlmOptions options) {
            return Flux.fromIterable(events);
        }
    }
}
