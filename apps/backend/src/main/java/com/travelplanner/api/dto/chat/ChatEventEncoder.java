package com.travelplanner.api.dto.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplanner.application.chat.ChatStreamEvent;
import org.springframework.http.codec.ServerSentEvent;

/**
 * Turns one {@link ChatStreamEvent} into one SSE frame (ADR 007).
 *
 * <p>The whole wire format lives here: {@code id:}, {@code event:}, {@code data:}, and the comment
 * frame a heartbeat is. Keeping it in one small class is what makes the contract testable as text —
 * {@code ChatSseContractTest} renders frames and feeds them to assertions written from the frontend
 * parser's rules, rather than trusting that a servlet container produced what everyone assumed.
 *
 * <p>Per request rather than a singleton bean, because a frame carries the correlation id of the
 * request that opened the stream. That id cannot come from the MDC at emission time: the frames are
 * produced on Reactor workers minutes after the request thread moved on, and an MDC read there would
 * return either nothing or another request's id.
 *
 * <h2>The heartbeat is a comment, not an event</h2>
 *
 * <p>ADR 007 specifies {@code : ping}. A comment frame is inert by construction — it has no
 * {@code event:} name and no {@code data:}, so no parser can mistake it for content, and a client
 * that has never heard of heartbeats ignores it for free. An {@code event: heartbeat} frame with an
 * empty body would have needed every consumer to know to discard it.
 */
public final class ChatEventEncoder {

    /** The comment body. Any text would do; this one is what ADR 007 writes. */
    static final String HEARTBEAT_COMMENT = "ping";

    private final ObjectMapper objectMapper;
    private final String requestId;

    public ChatEventEncoder(ObjectMapper objectMapper, String requestId) {
        this.objectMapper = objectMapper;
        this.requestId = requestId;
    }

    /**
     * One frame.
     *
     * <p>{@code id:} is present only on the frames that open or close a persisted message, and its
     * value is that message's {@code seq}. {@link ChatStreamEvent} explains why an id on every frame
     * would be worse than none: the frontend reducer drops any frame whose id is not ahead of the
     * last one applied, so repeating a message's {@code seq} across its own token deltas would
     * discard every token after the first.
     */
    public ServerSentEvent<String> encode(ChatStreamEvent event) {
        if (event instanceof ChatStreamEvent.Heartbeat) {
            return ServerSentEvent.<String>builder().comment(HEARTBEAT_COMMENT).build();
        }
        ServerSentEvent.Builder<String> frame = ServerSentEvent.<String>builder()
                .event(event.eventName())
                .data(serialise(ChatEventPayloads.of(event, requestId)));
        Long id = event.frameId();
        return id == null ? frame.build() : frame.id(id.toString()).build();
    }

    /**
     * @throws IllegalStateException when a payload cannot be serialised. Unreachable for the records
     *         in {@link ChatEventPayloads} — they hold strings, ints and UUIDs — and deliberately
     *         loud rather than swallowed, because a silently dropped frame in the middle of a turn
     *         is a class of bug that reproduces once a month and never in a test
     */
    private String serialise(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not serialise a chat stream frame", failure);
        }
    }
}
