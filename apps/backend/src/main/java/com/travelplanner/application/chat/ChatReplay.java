package com.travelplanner.application.chat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A reconnect answered from the database, with no provider call (ADR 007 resume; F-39).
 *
 * <p>A separate type from {@link ChatTurn} so the controller cannot confuse "this costs a generation"
 * with "this costs a query".
 *
 * @param frames every frame the client is missing, in {@code seq} order, ending in {@code done}.
 *        Materialised, not a {@code Flux}: it came from one query, and a lazy read would run after the
 *        transaction closed
 */
public record ChatReplay(UUID conversationId, List<ChatStreamEvent> frames) {

    public ChatReplay {
        Objects.requireNonNull(conversationId, "conversationId");
        frames = List.copyOf(frames);
    }
}
