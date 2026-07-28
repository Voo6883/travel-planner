package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The provider identity cannot be attached because it is already spoken for. Maps to
 * {@code 409 identity_already_linked} (PLAN §4.0.5).
 *
 * <p>Two distinct situations raise it, and they share a code because the caller's next move is the
 * same in both:
 *
 * <ul>
 *   <li>the {@code (provider, subject)} pair already belongs to a <em>different</em> account — one
 *       external identity may never resolve to two users, or "who am I?" would depend on which row
 *       a query happened to find first;
 *   <li>this account already has an identity for the provider. A second Google identity on one
 *       account would make {@code linked_providers} ambiguous and unlinking non-deterministic.
 * </ul>
 *
 * <p>The unique index on {@code (provider, provider_subject_id)} is the real arbiter; this check is
 * the readable one in front of it, so the common case is a typed 409 rather than a constraint
 * violation surfacing as a 500.
 */
public class IdentityAlreadyLinkedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "identity_already_linked";

    public IdentityAlreadyLinkedException() {
        super(CODE, "This provider account is already linked.", Map.of());
    }
}
