package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ConversationScope;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The assembled input for one model turn — what
 * {@link com.travelplanner.domain.port.ConversationContextPort} returns.
 *
 * <p>PLAN §3.2 defines the context of a turn as "user message history, {@code trip.status} (if
 * any), {@code TripBrief}, research/itinerary snapshot". Only the first two parts exist as of
 * tasks/20: the history, and which trip (if any) the thread belongs to. The brief and the snapshots
 * arrive with the tasks that own them (18, 21, 22) and will be added as components here, which is
 * why this is a record rather than a bare {@code List<Message>} — a later task widening the context
 * must not change every call site's signature.
 *
 * <p>{@link #truncated()} is not a detail. A window that silently dropped the oldest turns and a
 * window that happens to contain the whole conversation look identical from the message list alone,
 * and the caller must be able to tell: the first needs a summary of what was cut, the second does
 * not. Returning the flag is what stops "the agent forgot what I said" from being invisible.
 *
 * @param history oldest first — the order the model must receive it in, and the same order
 *        {@code uq_message_conversation_seq} produces
 */
public record ConversationContext(
        UUID conversationId,
        ConversationScope scope,
        UUID tripId,
        List<Message> history,
        boolean truncated) {

    public ConversationContext {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(scope, "scope");
        // Defensive copy: the caller assembling this holds the list it just read from the
        // repository, and a context that could be edited after it was built is not a snapshot.
        history = history == null ? List.of() : List.copyOf(history);
        if (scope.requiresTrip() != (tripId != null)) {
            throw ValidationFailedException.field("trip_id",
                    "a TRIP context must reference a trip and a PLANNER context must not");
        }
    }

    /** Builds the context of a conversation from history already loaded in {@code seq} order. */
    public static ConversationContext of(Conversation conversation, List<Message> history,
            boolean truncated) {
        Objects.requireNonNull(conversation, "conversation");
        return new ConversationContext(conversation.id(), conversation.scope(),
                conversation.tripId(), history, truncated);
    }

    /** True for the very first turn of a thread, where there is nothing to recall. */
    public boolean isEmpty() {
        return history.isEmpty();
    }

    /** Absent for a planner context — there is no trip yet. */
    public Optional<UUID> tripIfPresent() {
        return Optional.ofNullable(tripId);
    }

    /**
     * The sequence number of the newest message in the window, or empty when there is none. This
     * is the ADR 007 resume cursor a caller continues from, so it comes from the data rather than
     * being tracked separately and drifting.
     */
    public Optional<Long> latestSeq() {
        return history.isEmpty()
                ? Optional.empty()
                : Optional.of(history.get(history.size() - 1).seq());
    }
}
