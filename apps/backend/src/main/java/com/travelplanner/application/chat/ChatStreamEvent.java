package com.travelplanner.application.chat;

import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import com.travelplanner.domain.model.Message;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The wire event union, as the application layer produces it (ADR 007).
 *
 * <p><strong>This is not {@link com.travelplanner.domain.ai.LlmEvent}.</strong> That union is what
 * a <em>provider</em> can say; this one is what a <em>client</em> may be told, and the two differ in
 * both directions on purpose:
 *
 * <ul>
 *   <li>{@code message_start}, {@code message_end} and {@code heartbeat} exist only here — they are
 *       facts about a persisted message and about the connection, neither of which a model knows
 *       anything about.</li>
 *   <li>Everything a provider might add that is not on this list is <strong>dropped</strong>. The
 *       translation in {@link ChatTurnService} is an allow-list, not a mapping with a default
 *       branch, so a provider event this project has never seen cannot reach a browser. That is the
 *       tasks/20 rule "do not expose internal reasoning or raw provider events" expressed as a type
 *       rather than as a review note.</li>
 * </ul>
 *
 * <p>Sealed, mirroring {@code lib/api/chat-events.ts} on the frontend — ADR 007 requires the
 * hand-authored client to be matched by a closed backend type so that adding a variant is a compile
 * error on at least one side.
 *
 * <h2>Frame ids and what they mean</h2>
 *
 * <p>ADR 007 asks for a monotonic per-conversation {@code id} so a client can resume. Here an id is
 * emitted only on the frames that open or close a <em>persisted</em> message, and its value is that
 * message's {@code seq} — the same counter {@code uq_message_conversation_seq} orders history by.
 *
 * <p>The alternative, an id on every frame, would be actively wrong today: token deltas are not
 * persisted individually, so an id on a delta would promise a resume position that nothing can
 * replay, and — because the frontend reducer drops any frame whose id is not ahead of the last one
 * applied — repeating a message's {@code seq} across its own deltas would silently discard every
 * token after the first. Ids at persisted positions are the subset that is both monotonic and
 * honest. Replay of frames after {@code Last-Event-ID} is not implemented in this slice; the header
 * is accepted and ignored, and a reconnect re-sends the same {@code client_message_id}, which the
 * idempotency key already makes safe.
 */
public sealed interface ChatStreamEvent {

    /** The SSE {@code event:} name. Lower-case snake, matching the frontend parser's table. */
    String eventName();

    /**
     * The SSE {@code id:} value, or {@code null} when this frame is not a resumable position.
     * Boxed rather than an {@code OptionalLong} because it is written straight onto a frame.
     */
    default Long frameId() {
        return null;
    }

    /** True for the two frames after which the server closes: {@code done} and {@code error}. */
    default boolean isTerminal() {
        return false;
    }

    /**
     * A message opened.
     *
     * <p>{@code clientMessageId} is the load-bearing field. It is how the server's copy of a user
     * message is reconciled with the optimistic bubble already on screen
     * ({@code features/chat/lib/chat-state.ts}); without it the echo appends a second copy of what
     * the user is looking at. It is present only on a {@link ChatMessageRole#USER} message, because
     * only a client-authored message has one ({@code ck_message_client_id_is_user_only}).
     *
     * <p>{@code content} is present when the whole message is known up front — that same user echo.
     * An assistant turn arrives as deltas instead, so it opens with {@code content = null} rather
     * than with an empty string, which would be indistinguishable from "the model said nothing".
     */
    record MessageStart(UUID messageId, ChatMessageRole role, String clientMessageId, String content,
            Instant createdAt, long seq) implements ChatStreamEvent {

        public MessageStart {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(role, "role");
        }

        /** The echo of a message that is already complete — a user turn, or a replayed one. */
        public static MessageStart complete(Message message) {
            return new MessageStart(message.id(), message.role(), message.clientMessageId(),
                    message.content(), message.createdAt(), message.seq());
        }

        /** An assistant turn that has been persisted as {@code STREAMING} and has no text yet. */
        public static MessageStart streaming(Message message) {
            return new MessageStart(message.id(), message.role(), null, null, message.createdAt(),
                    message.seq());
        }

        @Override
        public String eventName() {
            return "message_start";
        }

        @Override
        public Long frameId() {
            return seq;
        }
    }

    /** A chunk of assistant prose. The only provider-authored text that reaches the wire. */
    record TextDelta(UUID messageId, String text) implements ChatStreamEvent {

        public TextDelta {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(text, "text");
        }

        @Override
        public String eventName() {
            return "text_delta";
        }
    }

    /**
     * How a message finished, and the point at which its row stopped being able to change.
     *
     * <p>Carries the full {@link ChatMessageStatus}, not a two-valued collapse: the server knows
     * whether a partial answer was cut by a disconnect ({@code interrupted}) or by a provider
     * failure ({@code failed}), and a reader that only needs "whole or not whole" can collapse it,
     * while one that discards the distinction here can never get it back.
     */
    record MessageEnd(UUID messageId, ChatMessageStatus status, long seq) implements ChatStreamEvent {

        public MessageEnd {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(status, "status");
        }

        @Override
        public String eventName() {
            return "message_end";
        }

        @Override
        public Long frameId() {
            return seq;
        }
    }

    /** ADR 007 {@code ToolUseStart}. Representable now; nothing emits it until tasks 21/22. */
    record ToolUseStart(String toolCallId, String name) implements ChatStreamEvent {

        @Override
        public String eventName() {
            return "tool_use_start";
        }
    }

    /** ADR 007 {@code ToolInputDelta}. The frontend deliberately drops the payload; §7.2. */
    record ToolInputDelta(String toolCallId, String jsonChunk) implements ChatStreamEvent {

        @Override
        public String eventName() {
            return "tool_input_delta";
        }
    }

    record ToolUseEnd(String toolCallId) implements ChatStreamEvent {

        @Override
        public String eventName() {
            return "tool_use_end";
        }
    }

    record ToolResult(String toolCallId, String payload) implements ChatStreamEvent {

        @Override
        public String eventName() {
            return "tool_result";
        }
    }

    /**
     * The handoff event (PLAN §3.2). Representable now, emitted by nothing: tasks/20 says "do not
     * add business tools or create trips", and ADR 007 requires this to follow a committed
     * {@code create_trip} rather than to be inferred from prose.
     */
    record TripCreated(UUID tripId) implements ChatStreamEvent {

        public TripCreated {
            Objects.requireNonNull(tripId, "tripId");
        }

        @Override
        public String eventName() {
            return "trip_created";
        }
    }

    /** Token accounting, normalised across providers. Nothing in the UI renders it. */
    record Usage(int inputTokens, int outputTokens, int cachedTokens) implements ChatStreamEvent {

        @Override
        public String eventName() {
            return "usage";
        }
    }

    /** The turn finished normally. The server closes after this. */
    record Done(String stopReason) implements ChatStreamEvent {

        @Override
        public String eventName() {
            return "done";
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    }

    /**
     * The PLAN §6.1 envelope, delivered in-band because the response committed to {@code 200} long
     * before the failure happened (ADR 007: "never a bare stream abort").
     *
     * <p>{@code code} is always a code registered in {@code api/openapi/errors.yaml} and
     * {@code ApiErrorCode}; an unregistered one renders to a user as a raw identifier. There is no
     * {@code details} component and no provider text: see {@link ChatTurnService} for why the
     * failure detail is logged rather than streamed.
     */
    record StreamError(String code, String message) implements ChatStreamEvent {

        public StreamError {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }

        @Override
        public String eventName() {
            return "error";
        }

        @Override
        public boolean isTerminal() {
            return true;
        }
    }

    /**
     * ADR 007's 15-second {@code : ping}. Written as a comment frame, so it carries nothing and
     * cannot be mistaken for content; its whole job is to stop a proxy reaping an idle connection
     * during a long model call.
     */
    record Heartbeat() implements ChatStreamEvent {

        @Override
        public String eventName() {
            return "heartbeat";
        }
    }
}
