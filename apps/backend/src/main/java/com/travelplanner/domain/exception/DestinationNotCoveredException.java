package com.travelplanner.domain.exception;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The requested destination is not curated to a depth that can be ranked honestly (ADR 010 §4).
 *
 * <p><strong>Why this is an error and not a low score.</strong> {@code fitScore} sums interest
 * match, seasonality, price fit, and area coverage. A destination with no seeded rows scores near
 * zero on three of those four terms and lands at the bottom of the list — which reads to the user
 * as "we considered Osaka and it is a poor fit for you", when the truth is "we have never looked at
 * Osaka". Those two statements are not interchangeable, and only one of them is true. ADR 010 §4
 * turns the second into this typed outcome so the agent can say it out loud.
 *
 * <p>The supported list travels with the failure because the honest answer is never just "no". A
 * caller that knows what <em>is</em> covered can offer it; one that only knows about the refusal
 * has to make a second call to be useful.
 */
public class DestinationNotCoveredException extends DomainException {

    private static final long serialVersionUID = 1L;

    /** Registered in {@code api/openapi/errors.yaml}; unregistered codes are downgraded. */
    public static final String CODE = "destination_not_covered";

    private final transient List<String> supportedSlugs;

    /**
     * @param requestedSlug what the caller asked for, echoed back so a typo is visible
     * @param supportedSlugs every destination that is {@code FULL}, in a stable order
     */
    public DestinationNotCoveredException(String requestedSlug, List<String> supportedSlugs) {
        super(CODE,
                "No curated travel knowledge exists for '" + requestedSlug + "' yet.",
                Map.of(
                        "requested", Objects.requireNonNull(requestedSlug, "requestedSlug"),
                        "supported", List.copyOf(Objects.requireNonNull(supportedSlugs, "supportedSlugs"))));
        this.supportedSlugs = List.copyOf(supportedSlugs);
    }

    /** The destinations the caller could ask for instead. */
    public List<String> supportedSlugs() {
        return supportedSlugs;
    }
}
