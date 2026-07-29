package com.travelplanner.domain.valueobject;

import com.travelplanner.domain.exception.ValidationFailedException;

/**
 * How many people are travelling, split by whether they are adults (PLAN §955).
 *
 * <p>Two counts rather than one total, because the split changes the answer rather than only the
 * arithmetic: room occupancy, entry pricing, and the {@code area_coverage} term of the C2 fit score
 * all treat a family of four differently from four adults. A single {@code travellers: 4} would
 * force every consumer to guess which one it meant.
 *
 * <p>Both counts are bounded. {@link #MAX_TRAVELLERS} is not a technical limit — it is the point
 * past which none of the suppliers in C4 quote as a single party, so a brief above it would produce
 * an itinerary that cannot be booked. Refusing it here is more honest than discovering it in C4.
 */
public record PartySize(int adults, int children) {

    /** A trip needs at least one adult; a party of children is not a case this product serves. */
    public static final int MIN_ADULTS = 1;

    /** Above this, C4 suppliers stop quoting a single party. */
    public static final int MAX_TRAVELLERS = 20;

    public PartySize {
        if (adults < MIN_ADULTS) {
            throw ValidationFailedException.field("party_size",
                    "must include at least " + MIN_ADULTS + " adult");
        }
        if (children < 0) {
            throw ValidationFailedException.field("party_size",
                    "children must not be negative");
        }
        if (adults + children > MAX_TRAVELLERS) {
            throw ValidationFailedException.field("party_size",
                    "must be at most " + MAX_TRAVELLERS + " travellers");
        }
    }

    /** A party with no children — the shape a single {@code NUMBER} clarification answer produces. */
    public static PartySize ofAdults(int adults) {
        return new PartySize(adults, 0);
    }

    /** Everyone travelling, adults and children together. */
    public int total() {
        return adults + children;
    }
}
