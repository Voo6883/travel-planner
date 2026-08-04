package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * No research job with that id belongs to the addressed trip. Maps to {@code 404 not_found}.
 *
 * <p>Reuses the registered {@code not_found} code rather than adding a job-specific one, for the
 * same reason {@link TripNotFoundException} does: the client's handling is identical to every other
 * 404, and the poll is only reachable after the trip has already been shown to be the caller's, so a
 * missing job is an ordinary "no such resource" and never an ownership leak.
 */
public class ResearchJobNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /** Registered in {@code api/openapi/errors.yaml}; unregistered codes are downgraded. */
    public static final String CODE = "not_found";

    public ResearchJobNotFoundException() {
        super(CODE, "The requested resource does not exist.", Map.of());
    }
}
