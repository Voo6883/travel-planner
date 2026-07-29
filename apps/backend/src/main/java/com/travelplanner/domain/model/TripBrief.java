package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The structured requirement set for one trip — what C1 produces and C2 consumes.
 *
 * <p>The editable half of this aggregate lives in {@link TripBriefDetails}; what remains here is
 * identity, the ADR 008 version, and the audit columns. That split is what lets a whole-body
 * {@code PUT} and a one-field clarification answer be the same operation, and it is why there is no
 * {@code withInterests} on this type: a field is set on the details, and the details are applied to
 * the aggregate exactly once, by {@link #withDetails(TripBriefDetails, Instant)}.
 *
 * <p>Immutable, like {@link Trip} and for the same reason: a setter could change the aggregate
 * between the moment its version was read and the moment it was written, which is precisely the
 * lost update ADR 008 exists to stop.
 *
 * <p>Every editable field is genuinely absent on a brief under construction. An incomplete brief is
 * answered with typed clarification (PLAN §4.1.3), not with a rejected save, so the record
 * components are nullable and {@link #budgetIfPresent()} / {@link #datesIfPresent()} are the
 * accessors callers should use — a record accessor cannot itself return {@link Optional} without
 * colliding with its own component type.
 *
 * <p><strong>Whether the brief is complete is not decided here.</strong>
 * {@link ClarificationNeeded#forDetails(TripBriefDetails)} owns that rule, so the questions a user
 * is asked and the definition of "complete" cannot drift apart — they are the same computation read
 * two ways.
 */
public record TripBrief(
        UUID id,
        UUID tripId,
        List<String> destinations,
        DateRange dates,
        DateFlexibility dateFlexibility,
        String departureCity,
        Money budget,
        PartySize party,
        List<TravelInterest> interests,
        TravelPace pace,
        int version,
        Instant createdAt,
        Instant updatedAt) implements Versioned {

    public TripBrief {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tripId, "tripId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        destinations = destinations == null ? List.of() : List.copyOf(destinations);
        interests = interests == null ? List.of() : List.copyOf(interests);
        if (version < 0) {
            throw ValidationFailedException.field("version", "must not be negative");
        }
    }

    /** An empty brief for a freshly created trip. Version 0 means "never persisted". */
    public static TripBrief createFor(UUID tripId, Instant now) {
        return new TripBrief(UUID.randomUUID(), tripId, List.of(), null, null, null, null, null,
                List.of(), null, 0, now, now);
    }

    /** The editable fields, as one value the clarification flow and the form save both operate on. */
    public TripBriefDetails details() {
        return new TripBriefDetails(destinations, dates, dateFlexibility, departureCity, budget,
                party, interests, pace);
    }

    /**
     * Replaces every editable field at once.
     *
     * <p>The version is carried through unchanged rather than incremented: the increment belongs to
     * the persistence provider's {@code @Version} handling, and a domain method that guessed at it
     * would produce an aggregate whose version does not match any row.
     *
     * <p>{@code now} is a parameter, not {@code Instant.now()}. A domain that reads a clock from a
     * static cannot be tested deterministically.
     */
    public TripBrief withDetails(TripBriefDetails details, Instant now) {
        Objects.requireNonNull(details, "details");
        return new TripBrief(id, tripId, details.destinations(), details.dates(),
                details.dateFlexibility(), details.departureCity(), details.budget(),
                details.party(), details.interests(), details.pace(), version, createdAt, now);
    }

    /** Absent until the user or the agent supplies a budget. */
    public Optional<Money> budgetIfPresent() {
        return Optional.ofNullable(budget);
    }

    /** Absent until the user or the agent supplies travel dates. */
    public Optional<DateRange> datesIfPresent() {
        return Optional.ofNullable(dates);
    }

    /** Convenience for the budget-only write path; equivalent to setting it on the details. */
    public TripBrief withBudget(Money newBudget, Instant now) {
        return withDetails(details().withBudget(newBudget), now);
    }

    /** Convenience for the dates-only write path; equivalent to setting it on the details. */
    public TripBrief withDates(DateRange newDates, Instant now) {
        return withDetails(details().withDates(newDates), now);
    }
}
