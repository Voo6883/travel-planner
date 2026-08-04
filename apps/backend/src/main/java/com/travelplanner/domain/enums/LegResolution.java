package com.travelplanner.domain.enums;

/**
 * How a leg's numbers were arrived at (task 29 DoD: "missing route information is explicit and
 * safe").
 *
 * <p><strong>This is the enum that keeps an estimate from being read as a measurement.</strong> A
 * duration with no provenance for how it was derived is the most quietly dangerous field in an
 * itinerary: thirty-five minutes looks identical whether somebody curated it or a fallback guessed
 * it, and a traveller misses a train on the difference. Every leg carries one of these, and the
 * column is {@code NOT NULL} because "we did not record how we knew" makes every other row
 * untrustworthy too.
 */
public enum LegResolution {

    /**
     * A curated {@code route_segment} between the two areas, cited by id.
     *
     * <p>The only resolution permitted to carry a {@code route_segment_id} — an estimate pointing at
     * a segment would be wearing a citation it did not earn.
     */
    CURATED_SEGMENT,

    /**
     * No segment for this pair, but both endpoints sit in known areas and the knowledge base has a
     * segment between those areas marked {@code estimated}.
     *
     * <p>Honest by construction: {@code RouteSegment.estimated()} already exists precisely so a
     * curated duration and a KB-level estimate stay distinguishable, and that flag is carried
     * through rather than flattened.
     */
    AREA_ESTIMATE,

    /**
     * Both stops are in the same curated area, so the leg is a short walk.
     *
     * <p>Not a guess about distance — a claim about topology. Areas are curated as neighbourhoods
     * one can cross on foot (TRAVEL-KNOWLEDGE-CATALOG §2.1), so "same area" is itself the fact. The
     * duration is a declared constant rather than a computed one, because computing it from
     * coordinates would imply a route nobody surveyed.
     */
    SAME_AREA_WALK,

    /**
     * The knowledge base cannot answer this leg.
     *
     * <p>Shown, not hidden, and it claims nothing: no duration, no cost band, no mode, no segment.
     * A leg quietly omitted reads as "these two stops are adjacent"; a leg labelled unknown tells
     * the traveller to check for themselves, which is the only safe thing to say when the corpus is
     * PARTIAL (ADR 010 §1).
     */
    UNKNOWN;

    /** Whether this resolution may carry a duration at all. */
    public boolean carriesDuration() {
        return this != UNKNOWN;
    }

    /** Whether the number came from a curated segment rather than a fallback. */
    public boolean isCurated() {
        return this == CURATED_SEGMENT;
    }

    /** UC-C3-09: whether the traveller should be told to check this leg themselves. */
    public boolean needsUserAttention() {
        return this == UNKNOWN;
    }
}
