package com.travelplanner.api.dto.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.application.chat.ChatStreamEvent;
import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

/**
 * The SSE wire contract, asserted as bytes.
 *
 * <p>ADR 007 makes the frontend's event union the single hand-authored client type in the project,
 * which means nothing regenerates and nothing fails to compile when a field name here changes. This
 * test is the substitute for that missing compiler: every assertion below is written from a rule
 * stated in {@code apps/frontend/src/lib/api/chat-events.ts}, so a change that would make the client
 * fall back to {@code ignored: invalid_payload} fails the backend build instead of showing a user a
 * silently empty answer.
 *
 * <p>The frames are rendered to text and read back through {@link #parse}, a deliberately literal
 * transcription of the client's parser rules — comment lines, one optional space after the colon,
 * blank line as the separator. Asserting on the {@code ServerSentEvent} object alone would prove
 * that the right builder methods were called, not that the frame a browser receives is parseable.
 */
class ChatSseContractTest {

    private static final UUID USER_MESSAGE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ASSISTANT_MESSAGE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    private static final String REQUEST_ID = "req-1234";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatEventEncoder encoder = new ChatEventEncoder(objectMapper, REQUEST_ID);

    @Test
    void theUserEchoCarriesEveryFieldTheClientReconcilesOn() {
        // `client_message_id` is the de-duplication channel (STATUS F-36 item 3). `content` and
        // `created_at` let the server's copy replace the optimistic bubble outright rather than
        // leaving a message on screen that the server has never confirmed.
        Frame frame = render(new ChatStreamEvent.MessageStart(USER_MESSAGE, ChatMessageRole.USER,
                "cmid-1", "Kyoto in spring?", NOW, 1L));

        assertThat(frame.event()).isEqualTo("message_start");
        assertThat(frame.id()).isEqualTo("1");
        assertThat(frame.data().get("message_id").asText()).isEqualTo(USER_MESSAGE.toString());
        assertThat(frame.data().get("role").asText()).isEqualTo("user");
        assertThat(frame.data().get("client_message_id").asText()).isEqualTo("cmid-1");
        assertThat(frame.data().get("content").asText()).isEqualTo("Kyoto in spring?");
        assertThat(frame.data().get("created_at").asText()).isEqualTo("2026-07-29T10:00:00Z");
    }

    @Test
    void theAssistantOpeningPublishesNullRatherThanOmittingTheFields() {
        // The client's schema is `.nullish()`, so both would parse — but an explicit null says "no
        // retry key exists for a server-authored message", where an absent key says nothing at all.
        Frame frame = render(new ChatStreamEvent.MessageStart(ASSISTANT_MESSAGE,
                ChatMessageRole.ASSISTANT, null, null, NOW, 2L));

        assertThat(frame.data().get("role").asText()).isEqualTo("assistant");
        assertThat(frame.data().has("client_message_id")).isTrue();
        assertThat(frame.data().get("client_message_id").isNull()).isTrue();
        assertThat(frame.data().get("content").isNull()).isTrue();
    }

    @Test
    void aTextDeltaCarriesItsMessageIdAndNoFrameId() {
        Frame frame = render(new ChatStreamEvent.TextDelta(ASSISTANT_MESSAGE, "Kyoto "));

        assertThat(frame.event()).isEqualTo("text_delta");
        assertThat(frame.id()).isNull();
        assertThat(frame.data().get("text").asText()).isEqualTo("Kyoto ");
        assertThat(frame.data().get("message_id").asText()).isEqualTo(ASSISTANT_MESSAGE.toString());
    }

    @Test
    void everyMessageEndStatusIsLowerCaseOnTheWire() {
        // STATUS F-31. `chat-events.ts` collapses anything that is not `complete` to `interrupted`;
        // that collapse is a semantic decision and stays. The case normalisation next to it does not.
        for (ChatMessageStatus status : ChatMessageStatus.values()) {
            Frame frame = render(new ChatStreamEvent.MessageEnd(ASSISTANT_MESSAGE, status, 2L));

            assertThat(frame.event()).isEqualTo("message_end");
            assertThat(frame.data().get("status").asText()).isEqualTo(ChatWireNames.of(status));
            assertThat(frame.data().get("status").asText()).doesNotContainPattern("[A-Z]");
        }
    }

    @Test
    void aHeartbeatIsACommentFrameWithNoEventNameAndNoData() {
        // ADR 007's `: ping`. The client turns a comment-only frame into `heartbeat`; an
        // `event: heartbeat` frame with an empty body would need every consumer to know to drop it.
        String text = renderText(new ChatStreamEvent.Heartbeat());

        assertThat(text).startsWith(":");
        assertThat(text).doesNotContain("event:").doesNotContain("data:");
        assertThat(parse(text).event()).isNull();
    }

    @Test
    void theTerminalErrorFrameIsTheSectionSixOneEnvelope() {
        Frame frame = render(new ChatStreamEvent.StreamError("ai_unavailable", "The model is away."));

        assertThat(frame.event()).isEqualTo("error");
        assertThat(frame.data().get("code").asText()).isEqualTo("ai_unavailable");
        assertThat(frame.data().get("details").isObject()).isTrue();
        assertThat(frame.data().get("details").isEmpty()).isTrue();
        // The correlation id travels in the body: the headers were flushed long before this failure.
        assertThat(frame.data().get("request_id").asText()).isEqualTo(REQUEST_ID);
    }

    @Test
    void doneCarriesALowerCaseStopReasonOrNull() {
        assertThat(render(new ChatStreamEvent.Done("end_turn")).data().get("stop_reason").asText())
                .isEqualTo("end_turn");
        assertThat(render(new ChatStreamEvent.Done(null)).data().get("stop_reason").isNull()).isTrue();
    }

    @Test
    void everyEventNameMatchesTheClientsParserTable() {
        // A name the client has no row for degrades to `ignored: unknown_event` — harmless, and
        // indistinguishable from a working stream that says nothing.
        List<ChatStreamEvent> events = List.of(
                new ChatStreamEvent.MessageStart(USER_MESSAGE, ChatMessageRole.USER, "c", "t", NOW, 1L),
                new ChatStreamEvent.TextDelta(ASSISTANT_MESSAGE, "x"),
                new ChatStreamEvent.MessageEnd(ASSISTANT_MESSAGE, ChatMessageStatus.COMPLETE, 2L),
                new ChatStreamEvent.ToolUseStart("call-1", "search"),
                new ChatStreamEvent.ToolInputDelta("call-1", "{"),
                new ChatStreamEvent.ToolUseEnd("call-1"),
                new ChatStreamEvent.ToolResult("call-1", "{}"),
                new ChatStreamEvent.TripCreated(UUID.randomUUID()),
                new ChatStreamEvent.BriefUpdated(UUID.randomUUID()),
                new ChatStreamEvent.Usage(1, 2, 3),
                new ChatStreamEvent.Done("end_turn"),
                new ChatStreamEvent.StreamError("internal_error", "no"));

        assertThat(events.stream().map(this::render).map(Frame::event).toList())
                .containsExactly("message_start", "text_delta", "message_end", "tool_use_start",
                        "tool_input_delta", "tool_use_end", "tool_result", "trip_created",
                        "brief_updated", "usage", "done", "error");
    }

    @Test
    void toolFramesUseTheSnakeCaseFieldNamesTheClientDeclares() {
        assertThat(render(new ChatStreamEvent.ToolUseStart("call-1", "search")).data().has("tool_call_id"))
                .isTrue();
        assertThat(render(new ChatStreamEvent.ToolInputDelta("call-1", "{")).data().has("json_chunk"))
                .isTrue();
        assertThat(render(new ChatStreamEvent.TripCreated(USER_MESSAGE)).data().has("trip_id")).isTrue();
        assertThat(render(new ChatStreamEvent.BriefUpdated(USER_MESSAGE)).data().has("trip_id")).isTrue();
        JsonNode usage = render(new ChatStreamEvent.Usage(1, 2, 3)).data();
        assertThat(usage.has("input_tokens")).isTrue();
        assertThat(usage.has("output_tokens")).isTrue();
        assertThat(usage.has("cached_tokens")).isTrue();
    }

    @Test
    void noFrameCanCarryAReasoningField() {
        // tasks/20 DoD: "no hidden chain-of-thought is stored or returned". The guarantee is
        // structural — these records are the complete set of payloads, and none of them has
        // anywhere to put one — but the negative is asserted directly because a field added in
        // haste would otherwise be caught by nothing.
        List<ChatStreamEvent> everyEvent = List.of(
                new ChatStreamEvent.MessageStart(USER_MESSAGE, ChatMessageRole.USER, "c", "t", NOW, 1L),
                new ChatStreamEvent.TextDelta(ASSISTANT_MESSAGE, "x"),
                new ChatStreamEvent.MessageEnd(ASSISTANT_MESSAGE, ChatMessageStatus.FAILED, 2L),
                new ChatStreamEvent.ToolUseStart("call-1", "search"),
                new ChatStreamEvent.ToolInputDelta("call-1", "{"),
                new ChatStreamEvent.ToolUseEnd("call-1"),
                new ChatStreamEvent.ToolResult("call-1", "{}"),
                new ChatStreamEvent.TripCreated(UUID.randomUUID()),
                new ChatStreamEvent.BriefUpdated(UUID.randomUUID()),
                new ChatStreamEvent.Usage(1, 2, 3),
                new ChatStreamEvent.Done("end_turn"),
                new ChatStreamEvent.StreamError("internal_error", "no"),
                new ChatStreamEvent.Heartbeat());

        for (ChatStreamEvent event : everyEvent) {
            String text = renderText(event).toLowerCase(java.util.Locale.ROOT);
            assertThat(text)
                    .doesNotContain("reasoning")
                    .doesNotContain("thinking")
                    .doesNotContain("chain_of_thought")
                    .doesNotContain("scratchpad")
                    .doesNotContain("raw_provider");
        }
    }

    @Test
    void theErrorFrameDoesNotEchoProviderSuppliedDetail() {
        // `ChatTurnService` strips the provider's own message before the frame is built; this
        // asserts the payload has no route back in even if a caller passed one.
        Frame frame = render(new ChatStreamEvent.StreamError("ai_timeout",
                "The conversation could not be completed."));

        assertThat(frame.data().get("details").isEmpty()).isTrue();
        assertThat(frame.data().fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "details", "request_id");
    }

    // ------------------------------------------------------------------------------------------
    // Framing. A literal transcription of the rules in `chat-events.ts`.
    // ------------------------------------------------------------------------------------------

    private Frame render(ChatStreamEvent event) {
        return parse(renderText(event));
    }

    /**
     * The bytes Spring's {@code SseEmitter} writes for one frame: {@code field:value} lines
     * terminated by a blank line, with {@code :} alone introducing a comment.
     */
    private String renderText(ChatStreamEvent event) {
        ServerSentEvent<String> frame = encoder.encode(event);
        StringBuilder text = new StringBuilder();
        if (frame.comment() != null) {
            text.append(':').append(frame.comment()).append('\n');
        }
        if (frame.id() != null) {
            text.append("id:").append(frame.id()).append('\n');
        }
        if (frame.event() != null) {
            text.append("event:").append(frame.event()).append('\n');
        }
        if (frame.data() != null) {
            text.append("data:").append(frame.data()).append('\n');
        }
        return text.append('\n').toString();
    }

    private Frame parse(String rawFrame) {
        String id = null;
        String event = null;
        StringBuilder data = new StringBuilder();
        for (String line : rawFrame.split("\n")) {
            if (line.isEmpty() || line.startsWith(":")) {
                continue;
            }
            int separator = line.indexOf(':');
            String field = separator == -1 ? line : line.substring(0, separator);
            String rawValue = separator == -1 ? "" : line.substring(separator + 1);
            String value = rawValue.startsWith(" ") ? rawValue.substring(1) : rawValue;
            if ("id".equals(field)) {
                id = value;
            } else if ("event".equals(field)) {
                event = value;
            } else if ("data".equals(field)) {
                data.append(value);
            }
        }
        return new Frame(id, event, readJson(data.toString()));
    }

    private JsonNode readJson(String data) {
        if (data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(data);
        } catch (Exception failure) {
            throw new AssertionError("a frame carried data that is not JSON: " + data, failure);
        }
    }

    private record Frame(String id, String event, JsonNode data) {
    }
}
