package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * Ranked recommendations are not available yet (UC-C2-03). Maps to {@code 409 research_not_ready}.
 *
 * <p>Raised when the trip is not {@code RESEARCH_READY} (or {@code DESTINATION_SELECTED} with a
 * persisted selection) — the client should poll the research job rather than treat this as a
 * permanent failure.
 */
public class ResearchNotReadyException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "research_not_ready";

    public ResearchNotReadyException() {
        super(CODE, "Research results are not ready for this trip yet.", Map.of());
    }
}
