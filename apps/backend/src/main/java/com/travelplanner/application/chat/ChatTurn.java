package com.travelplanner.application.chat;

import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.model.Message;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything a turn needs in order to stream, gathered while transactions were still open.
 *
 * <p>The handle exists so that the boundary is impossible to blur: it is produced by
 * {@link ChatTurnService#openTurn} — which commits — and consumed by
 * {@link ChatTurnService#stream}, which must not touch the database until the stream has ended. A
 * streaming method that took a conversation id instead would be one query away from re-opening a
 * transaction inside the model call, which is the exact failure tasks/20 forbids.
 *
 * <p>It also means an HTTP failure stays an HTTP failure. The controller calls {@code openTurn}
 * before it returns anything, so "no such trip" or "this thread is archived" is answered with a
 * status and the §6.1 envelope, rather than as an {@code event: error} on a response that has
 * already committed to {@code 200} for no reason.
 *
 * @param userMessage the committed user turn — the row the echo frame is built from, which on a
 *        retry is the row a previous attempt committed rather than a new one
 * @param assistantMessage the empty {@code STREAMING} row the answer will be written into
 */
public record ChatTurn(UUID conversationId, Message userMessage, Message assistantMessage, Prompt prompt) {

    public ChatTurn {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(assistantMessage, "assistantMessage");
        Objects.requireNonNull(prompt, "prompt");
    }
}
