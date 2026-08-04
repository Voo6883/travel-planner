package com.travelplanner.application.itinerary;

import com.travelplanner.domain.model.Itinerary;
import com.travelplanner.domain.model.ItineraryLeg;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What generation produced, or the typed reason it could not (task 30 DoD: "valid inputs produce a
 * feasible, sourced itinerary or typed failure").
 *
 * <p>A value rather than an exception for the failure path, because these are ordinary answers on a
 * PARTIAL corpus — a destination with four curated POIs cannot fill a ten-day trip, and that is
 * information the traveller should receive rather than a stack trace somebody greps for later.
 * Provider crashes and programming errors still throw.
 *
 * @param itinerary present exactly when {@link #succeeded()}
 * @param attempts how many proposals were tried, including the repair. Observable per the DoD's
 *        "tool/model loops are bounded and observable"
 */
public record ItineraryGenerationOutcome(
        Itinerary itinerary,
        List<ItineraryLeg> legs,
        Failure failure,
        String detail,
        int attempts) {

    public ItineraryGenerationOutcome {
        legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
        if ((itinerary == null) == (failure == null)) {
            throw new IllegalArgumentException(
                    "exactly one of itinerary and failure must be present");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be at least 1, got " + attempts);
        }
    }

    static ItineraryGenerationOutcome success(
            Itinerary itinerary, List<ItineraryLeg> legs, int attempts) {
        return new ItineraryGenerationOutcome(itinerary, legs, null, null, attempts);
    }

    static ItineraryGenerationOutcome failed(Failure failure, String detail, int attempts) {
        return new ItineraryGenerationOutcome(null, List.of(), failure, detail, attempts);
    }

    public boolean succeeded() {
        return itinerary != null;
    }

    public Optional<Itinerary> itineraryIfBuilt() {
        return Optional.ofNullable(itinerary);
    }

    public Optional<String> detailIfPresent() {
        return Optional.ofNullable(detail);
    }

    /** Why no plan was produced. Each is a distinct thing to tell the traveller. */
    public enum Failure {

        /** The trip was not in {@code DESTINATION_SELECTED}. A caller ordering problem. */
        WRONG_TRIP_STATUS,

        /** No destination has been chosen, so there is nothing to plan. */
        NO_SELECTED_DESTINATION,

        /**
         * The corpus holds too little for this destination to fill the trip.
         *
         * <p>The honest answer on a PARTIAL corpus (ADR 010 §1, F-34), and distinct from the model
         * failing: nothing went wrong, there simply is not enough curated to plan against.
         */
        INSUFFICIENT_KNOWLEDGE,

        /**
         * The agent's output could not be grounded, even after the repair attempt.
         *
         * <p>Fabricated POI ids, a duplicated place, the wrong number of days.
         */
        UNGROUNDED_PROPOSAL,

        /**
         * Every proposal was rejected by the scheduler as infeasible.
         *
         * <p>The validators staying authoritative, working exactly as the brief requires.
         */
        NOT_SCHEDULABLE,

        /** The provider failed or timed out. */
        PROVIDER_UNAVAILABLE
    }
}
