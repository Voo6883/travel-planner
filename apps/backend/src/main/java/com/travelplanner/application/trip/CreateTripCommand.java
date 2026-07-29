package com.travelplanner.application.trip;

/**
 * {@code POST /api/v1/trips} (PLAN §13: {@code *Command} for a write use case).
 *
 * <p>No {@code expectedVersion}. ADR 008 §2 versions edits to an existing aggregate, and there is
 * no prior state here for a concurrent writer to have replaced — the first version a created trip
 * has is the one this call produces.
 *
 * <p>No {@code userId} either. The owner comes from the {@code UserContext} the security filter
 * built from the session cookie, never from the body: an actor a request can assert is not an
 * actor anything may be attributed to.
 */
public record CreateTripCommand(String name) {
}
