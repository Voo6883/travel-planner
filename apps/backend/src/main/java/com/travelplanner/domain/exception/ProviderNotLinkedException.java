package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The account has no identity for the requested provider. Maps to {@code 404 not_found}.
 *
 * <p>Reuses the registered {@code not_found} code rather than introducing a provider-specific one:
 * this is the ordinary "no such resource owned by the caller" case, and the frontend's handling is
 * identical to every other 404. A new code would have to be registered, translated twice, and kept
 * in step forever to say the same thing.
 */
public class ProviderNotLinkedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "not_found";

    public ProviderNotLinkedException() {
        super(CODE, "The requested resource does not exist.", Map.of());
    }
}
