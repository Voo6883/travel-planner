package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The provider verified the caller but reports their address as unconfirmed. Maps to
 * {@code 403 firebase_email_not_verified} (PLAN §4.0.5).
 *
 * <p>Raised before any account is created or matched, because an unconfirmed address is not
 * evidence: it is a string the provider's user typed. Accepting it would let anyone who can create
 * a provider account claim any address in this system's namespace, which is the same failure ADR
 * 009 §4 closes on the linking side.
 *
 * <p>Distinguishable from {@link InvalidProviderTokenException} on purpose — the token itself was
 * perfectly valid, and the frontend's correct reaction is "confirm your address with Google", not
 * "try signing in again".
 */
public class ProviderEmailNotVerifiedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "firebase_email_not_verified";

    public ProviderEmailNotVerifiedException() {
        super(CODE, "This provider account has no verified email address.", Map.of());
    }
}
