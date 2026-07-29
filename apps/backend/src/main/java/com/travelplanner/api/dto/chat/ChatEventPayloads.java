package com.travelplanner.api.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.travelplanner.application.chat.ChatStreamEvent;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code data:} bodies of the SSE frames (ADR 007).
 *
 * <h2>Why every field name is written out</h2>
 *
 * <p>The rest of the API relies on the global {@code SNAKE_CASE} naming strategy, and that is right
 * for a body whose schema is published in {@code openapi.yaml} and consumed through a generated
 * client. These frames are not that. ADR 007 makes the event union the one <em>hand-authored</em>
 * client type in the project, precisely because no generator emits a usable
 * {@code text/event-stream} client — so nothing regenerates when a name changes here, and nothing
 * fails to compile. The names are therefore pinned with {@code @JsonProperty} rather than inferred
 * from a configuration property that a future change to {@code application.yml} could flip.
 *
 * <h2>What is not here</h2>
 *
 * <p>There is no reasoning field, no {@code thinking}, no raw provider event, and no place to put
 * one: these records are the complete set of things a chat stream can say, and
 * {@code ChatTurnService.translate} is an allow-list that maps only the {@code LlmEvent} variants
 * with a payload below. A provider event this project has not explicitly modelled has no frame to
 * arrive in. {@code ChatSseContractTest} asserts the negative directly.
 */
public final class ChatEventPayloads {

    private ChatEventPayloads() {
    }

    /**
     * The payload for one event, ready to serialise.
     *
     * @param requestId the correlation id of the request that opened the stream, used only by
     *        {@link ErrorEnvelope}. It is in the body rather than in {@code X-Request-Id} because the
     *        headers were flushed long before an in-band failure could happen
     * @return {@code null} for {@link ChatStreamEvent.Heartbeat}, which is a comment frame and
     *         carries no data at all
     */
    public static Object of(ChatStreamEvent event, String requestId) {
        return switch (event) {
            case ChatStreamEvent.MessageStart start -> new MessageStart(start.messageId(),
                    ChatWireNames.of(start.role()), start.clientMessageId(), start.content(),
                    start.createdAt() == null ? null : start.createdAt().toString());
            case ChatStreamEvent.TextDelta delta -> new TextDelta(delta.messageId(), delta.text());
            case ChatStreamEvent.MessageEnd end ->
                    new MessageEnd(end.messageId(), ChatWireNames.of(end.status()));
            case ChatStreamEvent.ToolUseStart start -> new ToolUseStart(start.toolCallId(), start.name());
            case ChatStreamEvent.ToolInputDelta delta ->
                    new ToolInputDelta(delta.toolCallId(), delta.jsonChunk());
            case ChatStreamEvent.ToolUseEnd end -> new ToolUseEnd(end.toolCallId());
            case ChatStreamEvent.ToolResult result -> new ToolResult(result.toolCallId(), result.payload());
            case ChatStreamEvent.TripCreated created -> new TripCreated(created.tripId());
            case ChatStreamEvent.Usage usage ->
                    new Usage(usage.inputTokens(), usage.outputTokens(), usage.cachedTokens());
            case ChatStreamEvent.Done done -> new Done(done.stopReason());
            case ChatStreamEvent.StreamError error ->
                    new ErrorEnvelope(error.code(), error.message(), Map.of(), requestId);
            case ChatStreamEvent.Heartbeat ignored -> null;
        };
    }

    /**
     * {@code client_message_id} is the field the whole optimistic-send design hangs on: it is how
     * the server's copy of a user message is matched to the bubble already on screen. Removing it
     * would not break a type — it would silently duplicate every message a user sends.
     */
    public record MessageStart(
            @JsonProperty("message_id") UUID messageId,
            @JsonProperty("role") String role,
            @JsonProperty("client_message_id") String clientMessageId,
            @JsonProperty("content") String content,
            @JsonProperty("created_at") String createdAt) {
    }

    public record TextDelta(
            @JsonProperty("message_id") UUID messageId,
            @JsonProperty("text") String text) {
    }

    public record MessageEnd(
            @JsonProperty("message_id") UUID messageId,
            @JsonProperty("status") String status) {
    }

    public record ToolUseStart(
            @JsonProperty("tool_call_id") String toolCallId,
            @JsonProperty("name") String name) {
    }

    public record ToolInputDelta(
            @JsonProperty("tool_call_id") String toolCallId,
            @JsonProperty("json_chunk") String jsonChunk) {
    }

    public record ToolUseEnd(@JsonProperty("tool_call_id") String toolCallId) {
    }

    public record ToolResult(
            @JsonProperty("tool_call_id") String toolCallId,
            @JsonProperty("payload") String payload) {
    }

    public record TripCreated(@JsonProperty("trip_id") UUID tripId) {
    }

    public record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens,
            @JsonProperty("cached_tokens") int cachedTokens) {
    }

    public record Done(@JsonProperty("stop_reason") String stopReason) {
    }

    /**
     * The PLAN §6.1 envelope, in-band.
     *
     * <p>{@code details} is always empty and that is not laziness. The provider's own message and
     * details are logged server-side and never streamed: §6.1 states that {@code message} is a
     * developer string the frontend never renders — it resolves {@code common.errors.<code>} instead
     * — so provider text on this frame would be leakage with no reader. The shape keeps the field so
     * the frame is the same envelope every other error in the app uses.
     */
    public record ErrorEnvelope(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("details") Map<String, Object> details,
            @JsonProperty("request_id") String requestId) {
    }
}
