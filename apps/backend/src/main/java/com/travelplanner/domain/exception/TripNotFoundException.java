package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * No trip with that id belongs to the caller. Maps to {@code 404 not_found}.
 *
 * <p><strong>A trip owned by somebody else raises this too, and that is the point.</strong>
 * PLAN §4.0.2-L scopes every query by the caller's user id, and
 * {@link com.travelplanner.domain.port.TripRepositoryPort} has no unscoped lookup to make the
 * distinction visible in the first place. A {@code 403} would answer "does this id exist?" for
 * anyone who cared to ask, turning the trip endpoint into an enumeration oracle over other
 * people's data; a {@code 404} for both cases answers nothing.
 *
 * <p>Reuses the registered {@code not_found} code rather than adding a {@code trip_not_found} one,
 * for the same reason {@link ProviderNotLinkedException} does: the client's handling is identical
 * to every other 404, and a second code would need registering, translating twice, and keeping in
 * step forever to say the same thing. {@code user_not_found} is the deliberate exception, and it
 * exists only because an administrator is already entitled to know which accounts exist.
 */
public class TripNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /** Registered in {@code api/openapi/errors.yaml}; unregistered codes are downgraded. */
    public static final String CODE = "not_found";

    public TripNotFoundException() {
        super(CODE, "The requested resource does not exist.", Map.of());
    }
}
