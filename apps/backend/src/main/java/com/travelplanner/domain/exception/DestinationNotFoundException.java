package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * No destination with that id exists in the knowledge base. Maps to {@code 404 not_found}.
 */
public class DestinationNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "not_found";

    public DestinationNotFoundException() {
        super(CODE, "The requested resource does not exist.", Map.of());
    }
}
