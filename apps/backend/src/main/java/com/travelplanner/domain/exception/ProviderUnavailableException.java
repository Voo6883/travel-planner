package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The identity provider could not be reached, timed out, or answered with a fault. Maps to
 * {@code 503 provider_unavailable}.
 *
 * <p>Deliberately not {@code internal_error}. This is somebody else's outage, nothing in this
 * system was created, linked, or revoked, and the caller's correct next action is to retry —
 * whereas {@code internal_error} tells a user to report a bug and tells an operator to look at a
 * stack trace that will only ever contain a socket timeout.
 *
 * <p>The distinction also matters for what it must <em>not</em> become: an outage must never be
 * quietly downgraded into "no such identity", because that would turn a network blip into an
 * account being created a second time.
 */
public class ProviderUnavailableException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "provider_unavailable";

    public ProviderUnavailableException(Throwable cause) {
        super(CODE, "The identity provider is unavailable. Please try again.", Map.of());
        initCause(cause);
    }
}
