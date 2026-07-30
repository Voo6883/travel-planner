package com.travelplanner.api.dto.chat;

import com.travelplanner.domain.model.Message;
import java.time.Instant;
import java.util.UUID;

/**
 * One stored message, as history returns it. Serialised snake_case by the global Jackson strategy,
 * so the wire fields are {@code message_id}, {@code client_message_id}, {@code created_at}.
 *
 * <p><strong>{@code seq} is published.</strong> It used to be withheld on the grounds that a client
 * has no use for the ordering the server already applied. That was wrong in a way the frontend then
 * demonstrated: a client merging two pages into one keyed collection needs a total order, and with
 * no ordinal on the wire the only candidate left was {@code created_at} — which cannot order a
 * conversation at all, because {@code now()} is fixed for a whole transaction in PostgreSQL and
 * every row a single turn writes shares one value byte for byte (migration V19's header states this
 * at length, and the client re-sorted by it anyway). Publishing the ordinal is also what ADR 007's
 * resume needs: {@code Last-Event-ID} is this number, so it is already on the wire in the SSE
 * frames.
 *
 * <p>Gaps are the documented caveat, not a reason to withhold it: {@code seq} is a total order and
 * nothing more. A client may compare two values and may pass one back as a cursor; it may not treat
 * the difference as a message count.
 *
 * <p><strong>Still deliberately absent.</strong> No {@code updated_at}, no {@code completed_at}, and
 * no {@code conversation_id} — the page carries the conversation once rather than on every row.
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
public record ChatHistoryMessageResponse(UUID messageId, long seq, String role, String content,
        String status, String clientMessageId, Instant createdAt) {

    public static ChatHistoryMessageResponse from(Message message) {
        return new ChatHistoryMessageResponse(message.id(), message.seq(),
                ChatWireNames.of(message.role()), message.content(),
                ChatWireNames.of(message.status()), message.clientMessageId(),
                message.createdAt());
    }
}
