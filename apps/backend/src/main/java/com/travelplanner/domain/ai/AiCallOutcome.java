package com.travelplanner.domain.ai;

/** How an AI call ended. Distinguishing cancellation from failure keeps error rates honest. */
public enum AiCallOutcome {

    /** Completed and returned a usable result. */
    OK,

    /** The provider or the platform failed; {@code errorCode} carries the normalised reason. */
    ERROR,

    /**
     * The caller went away — a closed SSE connection, an abandoned research job. Not a fault, and
     * counting it as one would make every user who navigates away look like an outage.
     */
    CANCELLED
}
