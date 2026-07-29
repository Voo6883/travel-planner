package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.ConversationScope;
import com.travelplanner.domain.enums.ConversationState;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One thread of messages, owned by a user and optionally attached to a trip (PLAN §3.2, §8).
 *
 * <p>Two shapes, one type. A {@link ConversationScope#PLANNER} conversation hangs off a
 * {@link PlannerSession} and has no trip; a {@link ConversationScope#TRIP} conversation is the
 * trip's one persistent thread, "from {@code create_trip} until archived". They are the same
 * aggregate because {@link #linkToTrip} turns the first into the second in place — the handoff has
 * to preserve the messages already said, and moving rows between two tables to do that is how a
 * history ends up half-migrated.
 *
 * <p><strong>{@link #nextMessageSeq()} is the ordering authority.</strong> It is the next number to
 * hand out, so a fresh conversation starts at 1 and no message ever carries 0. The counter lives
 * here, not on {@code message}, because allocating it must take a lock on exactly one row —
 * {@code MAX(seq) + 1} over the messages is a read that two appends can both perform and both
 * believe.
 *
 * <p><strong>No {@code version} column, deliberately.</strong> ADR 008 §1 names the aggregates that
 * get optimistic locking and this is not one. Two concurrent appends are both wanted; the right
 * answer is to queue the second, which is what the pessimistic row lock behind
 * {@link #recordAppend(Instant)} does. A {@code @Version} would turn them into a 409 instead.
 *
 * @param plannerSessionId where the thread started. Kept after the handoff as provenance, not as
 *        current state, so a trip conversation may carry both a {@code tripId} and this.
 * @param lastMessageAt denormalised for the "most recent first" conversation list; absent until the
 *        first message lands
 */
public record Conversation(
        UUID id,
        UUID userId,
        UUID tripId,
        UUID plannerSessionId,
        ConversationScope scope,
        ConversationState state,
        long nextMessageSeq,
        Instant lastMessageAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {

    /** Matches {@code conversation.next_message_seq bigint DEFAULT 1} — the first seq handed out. */
    public static final long FIRST_SEQ = 1L;

    public Conversation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        // Mirrors ck_conversation_scope_matches_trip. An equivalence, so both mistakes are caught:
        // a TRIP thread with no trip, and a PLANNER thread that quietly acquired one.
        if (scope.requiresTrip() != (tripId != null)) {
            throw ValidationFailedException.field("trip_id",
                    "a TRIP conversation must reference a trip and a PLANNER conversation must not");
        }
        // Mirrors ck_conversation_archived_paired.
        if (state.isReadOnly() != (archivedAt != null)) {
            throw ValidationFailedException.field("archived_at",
                    "must be present exactly when the conversation is ARCHIVED");
        }
        if (nextMessageSeq < FIRST_SEQ) {
            throw ValidationFailedException.field("next_message_seq",
                    "must be at least " + FIRST_SEQ);
        }
    }

    /** A pre-trip thread for the planner home. */
    public static Conversation startPlanner(UUID userId, UUID plannerSessionId, Instant now) {
        Objects.requireNonNull(plannerSessionId, "plannerSessionId");
        return new Conversation(UUID.randomUUID(), userId, null, plannerSessionId,
                ConversationScope.PLANNER, ConversationState.ACTIVE, FIRST_SEQ, null, null, now, now);
    }

    /**
     * A thread that belongs to a trip from the start — the trip created outside chat, from the
     * structured UI. The chat-first path uses {@link #startPlanner} and then {@link #linkToTrip}.
     */
    public static Conversation startForTrip(UUID userId, UUID tripId, Instant now) {
        Objects.requireNonNull(tripId, "tripId");
        return new Conversation(UUID.randomUUID(), userId, tripId, null,
                ConversationScope.TRIP, ConversationState.ACTIVE, FIRST_SEQ, null, null, now, now);
    }

    /** Ownership check for the user-scoping rule (PLAN §4.0.2-L). */
    public boolean isOwnedBy(UUID candidateUserId) {
        return userId.equals(candidateUserId);
    }

    /** Absent for a planner conversation that has not been handed off. */
    public Optional<UUID> tripIfPresent() {
        return Optional.ofNullable(tripId);
    }

    /** Absent for a conversation that was created directly against a trip. */
    public Optional<UUID> plannerSessionIfPresent() {
        return Optional.ofNullable(plannerSessionId);
    }

    /** Absent until the first message lands. */
    public Optional<Instant> lastMessageAtIfPresent() {
        return Optional.ofNullable(lastMessageAt);
    }

    /** True when new messages may be appended. False for an archived thread, which still reads. */
    public boolean isAppendable() {
        return !state.isReadOnly();
    }

    /**
     * The {@code create_trip} handoff (PLAN §3.2: "creates trip + links conversation").
     *
     * <p>{@code plannerSessionId} survives on purpose — it is the record of where the trip came
     * from, and the reason the pre-trip messages are still reachable as part of the trip's history.
     *
     * @throws ValidationFailedException when the conversation is already attached to a trip, or is
     *         archived. Re-linking is not idempotent: it would move a thread of one trip's history
     *         onto another trip.
     */
    public Conversation linkToTrip(UUID newTripId, Instant now) {
        Objects.requireNonNull(newTripId, "newTripId");
        requireAppendable();
        if (scope.requiresTrip()) {
            throw ValidationFailedException.field("trip_id",
                    "the conversation is already linked to a trip");
        }
        return new Conversation(id, userId, newTripId, plannerSessionId, ConversationScope.TRIP,
                state, nextMessageSeq, lastMessageAt, archivedAt, createdAt, now);
    }

    /**
     * Makes the thread read-only. History still loads — archiving is not deletion, and a user who
     * archives a trip has not asked to lose what was said about it.
     *
     * @throws ValidationFailedException when already archived, which would otherwise move
     *         {@code archived_at} forward and misreport when the thread was closed
     */
    public Conversation archive(Instant now) {
        requireAppendable();
        return new Conversation(id, userId, tripId, plannerSessionId, scope,
                ConversationState.ARCHIVED, nextMessageSeq, lastMessageAt, now, createdAt, now);
    }

    /**
     * Consumes {@link #nextMessageSeq()} and advances the counter — call this exactly once per
     * message appended, with the same {@code now} the message carries.
     *
     * <p>The allocated number is {@code nextMessageSeq()} <em>before</em> the call; this returns
     * the conversation to persist alongside the new message. Splitting it that way keeps the
     * aggregate a plain immutable record: a method that returned both the number and the new
     * conversation would need a tuple type whose only purpose is to be unpacked immediately.
     *
     * @throws ValidationFailedException when the conversation is archived
     */
    public Conversation recordAppend(Instant now) {
        requireAppendable();
        return new Conversation(id, userId, tripId, plannerSessionId, scope, state,
                nextMessageSeq + 1, now, archivedAt, createdAt, now);
    }

    /**
     * Advances {@code last_message_at} only, leaving {@link #nextMessageSeq()} exactly as it is.
     *
     * <p>The counterpart to {@link #recordAppend(Instant)}, for the caller that allocated its
     * sequence number through {@code ConversationRepositoryPort.allocateSequence} instead of from
     * this record. That allocation already advanced the stored counter under a row lock, so a
     * caller that then persisted {@code recordAppend}'s result — computed from a snapshot taken
     * <em>before</em> the allocation — would write the counter back one short and hand the same
     * number out twice. The second append would fail {@code uq_message_conversation_seq}, and it
     * would fail in a different request from the one that caused it.
     *
     * <p>Not "just an updated timestamp": {@code last_message_at} is what orders the conversation
     * list, so a thread that stopped advancing it sinks to the bottom while still being the one the
     * user is talking in.
     *
     * @throws ValidationFailedException when the conversation is archived. An archived thread has
     *         no appends to record, and a timestamp saying otherwise would be a lie in the column
     *         the list is ordered by
     */
    public Conversation touchLastMessageAt(Instant now) {
        requireAppendable();
        return new Conversation(id, userId, tripId, plannerSessionId, scope, state,
                nextMessageSeq, now, archivedAt, createdAt, now);
    }

    /**
     * @throws ValidationFailedException when the conversation is read-only. Public because the
     *         service layer needs the same refusal before it starts a model call — discovering the
     *         thread was archived after the tokens were paid for is too late.
     */
    public void requireAppendable() {
        if (!isAppendable()) {
            throw ValidationFailedException.field("state",
                    "an archived conversation cannot accept new messages");
        }
    }
}
