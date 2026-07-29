package com.travelplanner.domain.model;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The pre-trip planner chat context (PLAN §3.2 "Planner transport … (no {@code tripId})").
 *
 * <p>The planner home is chat-first: the user talks before any trip exists, so those messages need
 * an owner that is not a trip. This is it — a user-scoped handle the frontend can resume across
 * page loads with nothing else to point at.
 *
 * <p><strong>Not an authentication session.</strong> Despite the name it has nothing to do with
 * ADR 009: it is neither revocable nor a credential, and ending one signs nobody out. The name
 * comes from PLAN §8's table list, which is the vocabulary the rest of the plan uses.
 *
 * <p>Immutable, like every other aggregate here — {@link #end(Instant)} produces a new instance.
 * A session is closed rather than deleted when its conversation is handed off to a trip, because
 * the conversation keeps pointing at it and "this trip started from that planner chat" has to stay
 * answerable afterwards.
 */
public record PlannerSession(
        UUID id,
        UUID userId,
        Instant endedAt,
        Instant createdAt,
        Instant updatedAt) {

    public PlannerSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (endedAt != null && endedAt.isBefore(createdAt)) {
            throw ValidationFailedException.field("ended_at", "must not precede created_at");
        }
    }

    /**
     * A new, open session. At most one of these may exist per user at a time — enforced by the
     * partial unique index {@code uq_planner_session_user_open}, not by this factory, because two
     * browser tabs racing is a database-level fact rather than something a constructor can see.
     */
    public static PlannerSession open(UUID userId, Instant now) {
        return new PlannerSession(UUID.randomUUID(), userId, null, now, now);
    }

    /** Ownership check for the user-scoping rule (PLAN §4.0.2-L). */
    public boolean isOwnedBy(UUID candidateUserId) {
        return userId.equals(candidateUserId);
    }

    /** True while this is the planner chat to resume. */
    public boolean isOpen() {
        return endedAt == null;
    }

    /** Absent while the session is still open. */
    public Optional<Instant> endedAtIfPresent() {
        return Optional.ofNullable(endedAt);
    }

    /**
     * Closes the session — at the {@code create_trip} handoff, or when the user abandons it.
     *
     * @throws ValidationFailedException when the session is already closed. Closing twice is not
     *         harmless: it would move {@code ended_at} forward and misreport when the handoff
     *         actually happened, which is the one fact this column exists to record.
     */
    public PlannerSession end(Instant now) {
        if (!isOpen()) {
            throw ValidationFailedException.field("ended_at", "the session is already closed");
        }
        // A clock reading marginally behind createdAt (NTP correction, or a second instance later)
        // must not produce a session that ended before it began. Clamping is right rather than
        // throwing: the caller did nothing wrong, and the invariant is the thing worth preserving.
        Instant endInstant = now.isBefore(createdAt) ? createdAt : now;
        return new PlannerSession(id, userId, endInstant, createdAt, endInstant);
    }
}
