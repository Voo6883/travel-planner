package com.travelplanner.api.dto.chat;

import com.travelplanner.application.chat.ChatTarget;
import com.travelplanner.application.chat.SendChatMessageCommand;
import com.travelplanner.domain.model.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The body of both chat {@code POST}s (ADR 007, STATUS <b>F-36</b>).
 *
 * <p>The frontend authored {@code {client_message_id, content, conversation_id?}} against no
 * published contract, because the routes did not exist yet. This record adopts that shape unchanged
 * — it was the right one — and publishing it is what turns the assumption into a contract:
 * {@code openapi.yaml} now documents it, and the description-only streaming body ADR 007 asks for
 * refers to it by name.
 *
 * <h2>{@code client_message_id} is required, and is not a nicety</h2>
 *
 * <p>It is the idempotency key. The client mints it before sending and reuses it verbatim on every
 * retry, {@code uq_message_conversation_client_id} refuses a second insert under it, and it comes
 * back on the {@code message_start} echo so the optimistic bubble already on screen is updated
 * rather than duplicated. A send without one is a send a dropped response can commit twice, which is
 * why it is {@code @NotBlank} rather than optional-with-a-generated-fallback: a server-generated key
 * is different on the retry and would therefore guarantee the duplicate it was meant to prevent.
 *
 * @param conversationId optional. Absent on the first planner turn, which is the request that
 *        <em>creates</em> the conversation; a trip's thread is addressed by its path and does not
 *        need one either. Supplied, it must be the caller's and must belong to the addressed
 *        surface, or the answer is {@code 404}
 */
public record SendChatMessageRequest(
        @NotBlank @Size(max = Message.MAX_CLIENT_MESSAGE_ID_LENGTH) String clientMessageId,
        @NotBlank @Size(max = SendChatMessageCommand.MAX_CONTENT_LENGTH) String content,
        UUID conversationId) {

    /** Binds the request to the surface its path addressed. */
    public SendChatMessageCommand toCommand(ChatTarget target) {
        return new SendChatMessageCommand(target, conversationId, clientMessageId, content);
    }
}
