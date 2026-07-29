package com.travelplanner.application.chat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Which conversation a chat call addresses (PLAN §3.2).
 *
 * <p>Two surfaces share one transport: the planner home, which has no trip yet because it is the
 * surface that <em>creates</em> one, and a trip's own thread. The distinction is modelled as a type
 * rather than as a nullable {@code tripId} parameter for the same reason the frontend's
 * {@code ChatTarget} is a discriminated union — a {@code null} trip id sliding into a lookup is how
 * a planner request ends up asking the database for the conversation of trip {@code null}.
 *
 * @param tripId the trip whose thread is addressed, or {@code null} for the planner surface
 */
public record ChatTarget(UUID tripId) {

    /** Pre-trip planner chat. Resolves to the caller's open {@code planner_session}. */
    public static ChatTarget planner() {
        return new ChatTarget(null);
    }

    /** A trip's one persistent conversation. */
    public static ChatTarget trip(UUID tripId) {
        Objects.requireNonNull(tripId, "tripId");
        return new ChatTarget(tripId);
    }

    public boolean isPlanner() {
        return tripId == null;
    }

    public Optional<UUID> tripIfPresent() {
        return Optional.ofNullable(tripId);
    }
}
