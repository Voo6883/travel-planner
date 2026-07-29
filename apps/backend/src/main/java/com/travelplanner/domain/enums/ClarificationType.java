package com.travelplanner.domain.enums;

/**
 * What kind of value a {@link com.travelplanner.domain.model.ClarificationQuestion} is asking for
 * (UC-C1-04, PLAN §4.1.3).
 *
 * <p>The type is what makes the clarification flow renderable without a hand-written branch per
 * question: the frontend picks a widget from this, and the backend picks which slot of a
 * {@link com.travelplanner.domain.model.ClarificationAnswer} it will read. A question whose type
 * and answer disagree is a {@code validation_failed} rather than a silently ignored answer —
 * "never silently guess" (PLAN §3.1) applies to the answer just as much as to the question.
 */
public enum ClarificationType {

    /** Free text — a departure city, for now the only one. */
    TEXT,

    /** A whole number, such as a party size. */
    NUMBER,

    /** An amount plus an ISO 4217 currency. Never a bare number (PLAN §4.0.2-A). */
    MONEY,

    /** An inclusive pair of calendar dates. */
    DATE_RANGE,

    /** Exactly one of {@code options}. */
    CHOICE,

    /** One or more of {@code options}. */
    MULTI_CHOICE
}
