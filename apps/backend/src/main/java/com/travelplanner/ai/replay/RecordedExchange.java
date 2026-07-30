package com.travelplanner.ai.replay;

import com.travelplanner.domain.ai.StopReason;
import java.util.List;
import java.util.Objects;

/**
 * One recorded provider exchange: what was asked, and what came back (review §6.I).
 *
 * <h2>Why this exists alongside the stub</h2>
 *
 * <p>{@code StubLlmAdapter} is deterministic, needs no credentials, and is the right default — but it
 * answers {@code [stub] no model configured. Received: …} to everything. That is deliberately useless
 * as content, which is exactly what makes it useless as a <em>protocol</em> fixture. Nothing in the
 * test suite ever sees what a real Anthropic tool-call delta sequence looks like, or an OpenAI
 * response whose JSON arrives wrapped in prose, or a stream whose usage frame lands after {@code Done}.
 * The first time the system meets any of those is in production.
 *
 * <p>A recorded exchange is the other half: real provider output, replayed offline. The review's
 * phrasing — "保留真实协议兼容性", preserve real protocol compatibility — is the point. Cost, latency
 * variance, and flakiness go away; the shapes stay.
 *
 * <h2>Keyed by prompt hash, not by call order</h2>
 *
 * <p>{@link #promptHash} is {@code PromptHasher}'s digest, the same value {@code ai_call_log} stores.
 * Order-based replay — first call gets the first fixture — breaks the moment a test adds a call, and
 * breaks silently: every later assertion then compares against the wrong recording. A hash means a
 * fixture matches the prompt it was recorded for or matches nothing.
 *
 * <p>It also means a prompt change <em>invalidates</em> its fixture, loudly, which is correct. A
 * versioned prompt whose recording predates the version is not a valid test input, and
 * {@code TripBriefExtractionPrompt} gates on the SHA of the rendered block for the same reason.
 *
 * <h2>Redaction is the recorder's job, not this type's</h2>
 *
 * <p>No prompt text is stored — only its hash. That is not merely privacy hygiene: a fixture file
 * containing a traveller's message is a file that must not be committed, and a fixture that cannot be
 * committed is not a fixture. {@link ProviderRecorder} is where the rule is enforced, and
 * {@code RecordedExchangeTest} asserts it.
 *
 * @param provider which adapter produced this, so a fixture recorded against Anthropic is never
 *     replayed as OpenAI — the two differ in exactly the ways a replay suite exists to cover
 * @param completion the blocking result, or {@code null} for a stream-only recording
 * @param events the streamed events in arrival order, or empty for a completion-only recording
 */
public record RecordedExchange(
        String promptHash,
        String provider,
        String model,
        RecordedCompletion completion,
        List<RecordedEvent> events) {

    public RecordedExchange {
        Objects.requireNonNull(promptHash, "promptHash");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        events = events == null ? List.of() : List.copyOf(events);

        if (promptHash.isBlank()) {
            throw new IllegalArgumentException("promptHash must not be blank — it is the lookup key");
        }
        // A recording with neither half cannot serve any call, and a replay adapter that returned it
        // would fail with "no fixture" pointing at a fixture that exists. Refuse it at load.
        if (completion == null && events.isEmpty()) {
            throw new IllegalArgumentException(
                    "recorded exchange " + promptHash + " has neither a completion nor any events");
        }
    }

    public boolean canServeCompletion() {
        return completion != null;
    }

    public boolean canServeStream() {
        return !events.isEmpty();
    }

    /**
     * The blocking half of a recording.
     *
     * @param toolCalls the model's tool <em>requests</em>, replayed verbatim. Arguments are recorded
     *     as the provider sent them — including malformed ones, which are the interesting fixtures
     */
    public record RecordedCompletion(String text, List<RecordedToolCall> toolCalls, RecordedUsage usage,
            StopReason stopReason) {

        public RecordedCompletion {
            text = text == null ? "" : text;
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            usage = usage == null ? RecordedUsage.none() : usage;
            Objects.requireNonNull(stopReason, "stopReason");
        }
    }

    public record RecordedToolCall(String id, String name, String argumentsJson) {

        public RecordedToolCall {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            argumentsJson = argumentsJson == null ? "" : argumentsJson;
        }
    }

    public record RecordedUsage(int inputTokens, int outputTokens, int cachedTokens) {

        public static RecordedUsage none() {
            return new RecordedUsage(0, 0, 0);
        }
    }

    /**
     * One event of a recorded stream, in a flat shape a fixture file can hold.
     *
     * <p>Flat rather than a mirror of {@code LlmEvent}'s sealed hierarchy. Jackson can be taught to
     * round-trip a sealed interface with type ids, and it is the wrong trade here: the fixture format
     * would then be coupled to the union's Java shape, so renaming a record would silently invalidate
     * every committed recording. A {@code type} discriminant plus nullable fields is uglier to read
     * and survives a refactor, and {@link RecordedEventMapper} is the one place that knows the mapping.
     *
     * @param type the discriminant — {@code text_delta}, {@code tool_use_start}, {@code usage}, and so
     *     on, in the same lower-snake vocabulary the SSE wire uses
     */
    public record RecordedEvent(
            String type,
            String text,
            String toolCallId,
            String name,
            String jsonChunk,
            String payload,
            RecordedUsage usage,
            StopReason stopReason,
            String errorCode,
            String errorMessage) {

        public RecordedEvent {
            Objects.requireNonNull(type, "type");
            if (type.isBlank()) {
                throw new IllegalArgumentException("a recorded event needs a type discriminant");
            }
        }
    }
}
