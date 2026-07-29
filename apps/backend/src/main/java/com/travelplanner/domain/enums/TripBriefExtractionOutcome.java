package com.travelplanner.domain.enums;

/**
 * How a natural-language extraction attempt ended (task 19; PLAN §4.1, §4.1.3).
 *
 * <p>Two values, not three. There is no {@code PARTIAL}: an extraction that filled four fields out
 * of seven is still {@link #EXTRACTED} — the missing three are answered by
 * {@link com.travelplanner.domain.model.ClarificationNeeded}, which already is the project's
 * vocabulary for "incomplete". A third constant would give callers a second, competing way to ask
 * the same question and the two would eventually disagree.
 */
public enum TripBriefExtractionOutcome {

    /**
     * The model answered, the answer bound to the schema, and every value it produced was put
     * through the same domain invariants a form submission goes through. Values that failed are
     * listed in {@code unresolvedFields} and are asked about rather than guessed.
     */
    EXTRACTED,

    /**
     * The model did not produce usable output — a timeout, an unavailable provider, or malformed
     * structured output that survived the bounded repair attempt.
     *
     * <p>The brief is left exactly as it was and every outstanding field is asked as a
     * clarification question, which is the deterministic form fallback PLAN §4.1 requires: a model
     * failure degrades to the manual intake path, never to an invented brief.
     */
    FALLBACK
}
