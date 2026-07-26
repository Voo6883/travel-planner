package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The trip aggregate root. Everything a user plans hangs off one of these, and every query for it
 * is scoped by {@link #userId()} — one user owns a trip, and there is no sharing model
 * ({@code docs/AGENT-HARNESS.md} §1).
 *
 * <p>Immutable: a state change produces a new instance rather than mutating this one. That is what
 * makes {@link #version()} trustworthy — a setter could change the aggregate after the version was
 * read and before it was written, which is precisely the lost update ADR 008 exists to stop.
 *
 * <p>Which status transitions are legal is <em>not</em> decided here. Task 18 owns
 * {@code DRAFT}/{@code CLARIFICATION_NEEDED}/{@code BRIEF_COMPLETE}, and later tasks own the rest;
 * this type only refuses to move a trip that is already read-only.
 *
 * @param selectedRecommendationId the chosen {@code ranked_recommendation}, absent until C2
 *        completes. Not a foreign key yet — that table arrives with task 21.
 * @param createdAt UTC instant, never a local date-time (PLAN §4.0.2-H)
 */
public record Trip(
        UUID id,
        UUID userId,
        String name,
        TripStatus status,
        UUID selectedRecommendationId,
        int version,
        Instant createdAt,
        Instant updatedAt) implements Versioned {

    /** Matches {@code trip.name varchar(120)}; the database is the backstop, this is the message. */
    public static final int MAX_NAME_LENGTH = 120;

    public Trip {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        name = requireValidName(name);
        if (version < 0) {
            throw ValidationFailedException.field("version", "must not be negative");
        }
    }

    /** A brand-new {@code DRAFT} trip. Version 0 means "never persisted". */
    public static Trip create(UUID userId, String name, Instant now) {
        return new Trip(UUID.randomUUID(), userId, name, TripStatus.DRAFT, null, 0, now, now);
    }

    public Optional<UUID> selectedRecommendation() {
        return Optional.ofNullable(selectedRecommendationId);
    }

    /** Ownership check for the user-scoping rule (PLAN §4.0.2-L). */
    public boolean isOwnedBy(UUID candidateUserId) {
        return userId.equals(candidateUserId);
    }

    /**
     * @throws ValidationFailedException when the trip is archived — archived trips are view-only
     *         for every actor, the agent included
     */
    public Trip withStatus(TripStatus newStatus, Instant now) {
        requireMutable();
        return new Trip(id, userId, name, Objects.requireNonNull(newStatus, "newStatus"),
                selectedRecommendationId, version, createdAt, now);
    }

    public Trip rename(String newName, Instant now) {
        requireMutable();
        return new Trip(id, userId, newName, status, selectedRecommendationId, version, createdAt, now);
    }

    private void requireMutable() {
        if (status.isReadOnly()) {
            throw ValidationFailedException.field("status", "an archived trip cannot be modified");
        }
    }

    private static String requireValidName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw ValidationFailedException.field("name", "must not be blank");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw ValidationFailedException.field("name",
                    "must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }
}
