package com.travelplanner.api.dto.chat;

import com.travelplanner.domain.model.Message;
import java.time.Instant;
import java.util.UUID;

/**
 * One stored message, as history returns it. Serialised snake_case by the global Jackson strategy,
 * so the wire fields are {@code message_id}, {@code client_message_id}, {@code created_at}.
 *
 * <p><strong>What is deliberately absent.</strong> No {@code seq}, no {@code updated_at}, no
 * {@code completed_at}, and no {@code conversation_id} — the page carries the conversation once
 * rather than on every row. {@code seq} is the ordering the server sorts by, not a value a client
 * has any use for, and publishing it would invite a client to do arithmetic on a counter that is
 * allowed to contain gaps.
 *
 * <p>{@code role} and {@code status} are lower-case snake — see {@link ChatWireNames}. A user
 * message carries {@code status: "complete"} rather than nothing: it is complete on arrival, and an
 * omitted field would make every reloaded user message indistinguishable from one whose status the
 * server declined to state.
 *
 * <p>{@code tool_call_id}, {@code tool_name} and the tool roles are reachable through this record —
 * a {@code TOOL_CALL} row would come back with {@code role: "tool_call"} and its JSON in
 * {@code content} — but nothing writes those rows yet (tasks/20: no business tools). The correlation
 * ids are not published, because §7.2 forbids showing internal tool identifiers to a user and a
 * field that is published is a field something will eventually render.
 */
public record ChatHistoryMessageResponse(UUID messageId, String role, String content, String status,
        String clientMessageId, Instant createdAt) {

    public static ChatHistoryMessageResponse from(Message message) {
        return new ChatHistoryMessageResponse(message.id(), ChatWireNames.of(message.role()),
                message.content(), ChatWireNames.of(message.status()), message.clientMessageId(),
                message.createdAt());
    }
}
