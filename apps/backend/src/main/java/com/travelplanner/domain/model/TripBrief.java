package com.travelplanner.domain.model;

import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The structured requirement set for one trip — what C1 produces and C2 consumes.
 *
 * <p><strong>Foundation only.</strong> {@code tasks/18-trip-brief-core.md} owns the complete
 * shape (destination preferences, date flexibility, departure, party, interests, pace) and adds
 * those fields together with their migration. What is here is the part every later field depends
 * on: the one-to-one link to a trip, the ADR 008 version, and the two value objects whose
 * persistence mapping the rest of the aggregate reuses.
 *
 * <p>Both {@code budget} and {@code dates} are genuinely absent on a brief under construction: an
 * incomplete brief is answered with typed clarification (PLAN §4.1.3), not with a rejected save.
 * The record components are therefore nullable, and {@link #budgetIfPresent()} /
 * {@link #datesIfPresent()} are the accessors callers should use — a record accessor cannot itself
 * return {@link Optional} without colliding with its own component type.
 */
public record TripBrief(
        UUID id,
        UUID tripId,
        Money budget,
        DateRange dates,
        int version,
        Instant createdAt,
        Instant updatedAt) implements Versioned {

    public TripBrief {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tripId, "tripId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) {
            throw ValidationFailedException.field("version", "must not be negative");
        }
    }

    /** An empty brief for a freshly created trip. Version 0 means "never persisted". */
    public static TripBrief createFor(UUID tripId, Instant now) {
        return new TripBrief(UUID.randomUUID(), tripId, null, null, 0, now, now);
    }

    /** Absent until the user or the agent supplies a budget. */
    public Optional<Money> budgetIfPresent() {
        return Optional.ofNullable(budget);
    }

    /** Absent until the user or the agent supplies travel dates. */
    public Optional<DateRange> datesIfPresent() {
        return Optional.ofNullable(dates);
    }

    /**
     * Replaces the budget. Kept to two parameters, so the "now" clock value stays explicit rather
     * than being read from a static inside the domain — a domain that calls {@code Instant.now()}
     * cannot be tested deterministically.
     */
    public TripBrief withBudget(Money newBudget, Instant now) {
        return new TripBrief(id, tripId, newBudget, dates, version, createdAt, now);
    }

    public TripBrief withDates(DateRange newDates, Instant now) {
        return new TripBrief(id, tripId, budget, newDates, version, createdAt, now);
    }
}
