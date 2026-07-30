package com.travelplanner.application.chat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A reconnect answered from the database, with no provider call (ADR 007 resume; closes <b>F-39</b>).
 *
 * <p>The counterpart to {@link ChatTurn}: both are produced by {@link ChatTurnService} while
 * transactions are still open and consumed after the response has committed, but a turn ends in a
 * model call and this ends in a list of frames that already exist. Two types rather than one with a
 * nullable prompt, because "this costs a generation" and "this costs a query" are the two things the
 * controller most needs to be unable to confuse.
 *
 * @param frames every frame the client is missing, in {@code seq} order, ending in {@code done}. Fully
 *        materialised rather than a {@code Flux}: it came from one query, and pretending it is a
 *        stream would invite a lazy read that runs after the transaction closed
 */
public record ChatReplay(UUID conversationId, List<ChatStreamEvent> frames) {

    public ChatReplay {
        Objects.requireNonNull(conversationId, "conversationId");
        frames = List.copyOf(frames);
    }
}
