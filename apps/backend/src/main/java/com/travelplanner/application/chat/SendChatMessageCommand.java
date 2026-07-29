package com.travelplanner.application.chat;

import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.Message;
import java.util.UUID;

/**
 * One user turn, as the application layer receives it.
 *
 * <p>The wire shape it is built from is published in {@code openapi.yaml} as
 * {@code SendChatMessageRequest}: {@code {client_message_id, content, conversation_id?}}. That was
 * an assumption the frontend made before this endpoint existed (STATUS F-36); it is now the
 * contract, unchanged, because it is the right one — the retry key has to travel with the send it
 * makes idempotent, and the conversation is optional precisely because the first planner turn is
 * what creates the conversation.
 *
 * <p>Validated here as well as by Bean Validation on the DTO. HTTP is not the only caller a service
 * ever acquires, and an idempotency key that is only checked by the web layer is one a background
 * retry can omit.
 *
 * @param conversationId continues an existing thread; {@code null} resolves or opens one from
 *        {@code target}
 * @param clientMessageId the client-minted retry key. The reason a dropped response cannot commit
 *        the same message twice
 */
public record SendChatMessageCommand(ChatTarget target, UUID conversationId, String clientMessageId,
        String content) {

    /** Matches {@code SendChatMessageRequest.content} in the contract. */
    public static final int MAX_CONTENT_LENGTH = 8000;

    public SendChatMessageCommand {
        if (target == null) {
            throw ValidationFailedException.field("target", "must not be null");
        }
        clientMessageId = requireValidClientMessageId(clientMessageId);
        content = requireValidContent(content);
    }

    private static String requireValidClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw ValidationFailedException.field("client_message_id", "must not be blank");
        }
        if (clientMessageId.length() > Message.MAX_CLIENT_MESSAGE_ID_LENGTH) {
            throw ValidationFailedException.field("client_message_id",
                    "must be at most " + Message.MAX_CLIENT_MESSAGE_ID_LENGTH + " characters");
        }
        return clientMessageId;
    }

    private static String requireValidContent(String content) {
        if (content == null || content.isBlank()) {
            throw ValidationFailedException.field("content", "must not be blank");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw ValidationFailedException.field("content",
                    "must be at most " + MAX_CONTENT_LENGTH + " characters");
        }
        return content;
    }
}
