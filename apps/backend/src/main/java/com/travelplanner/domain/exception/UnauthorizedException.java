package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * No usable session. Maps to {@code 401 unauthorized}, the code already registered by task 06.
 *
 * <p>Raised where a <em>service</em> discovers the caller has no session — most of all the refresh
 * endpoint, where a missing, unknown, expired, revoked, or replayed refresh token all end here
 * with the same message. Requests that simply arrive without a valid access-token cookie never
 * reach a service at all: the security filter leaves them unauthenticated and the entry point
 * writes the identical envelope.
 */
public class UnauthorizedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "unauthorized";

    public UnauthorizedException() {
        super(CODE, "Authentication is required.", Map.of());
    }
}
