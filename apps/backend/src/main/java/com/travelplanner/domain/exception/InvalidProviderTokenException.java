package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * A provider-issued token did not verify. Maps to {@code 401 invalid_firebase_token} — the code
 * PLAN §4.0.5 registers for this condition.
 *
 * <p><strong>One exception for every cause</strong>, exactly as {@link InvalidCredentialsException}
 * is: a malformed token, a bad signature, an expired one, a token minted for a different Firebase
 * project ({@code aud}), and a token whose {@code firebase.sign_in_provider} is not
 * {@code google.com} all raise this with no {@code details}.
 *
 * <p>The last two are the ADR 009 §4 assertions, and folding them in with the rest is deliberate.
 * A distinct "wrong sign-in provider" code would tell somebody probing the endpoint precisely which
 * Firebase console setting to go after — the path ADR 009 §4 exists to close is one that opens by
 * enabling Email/Password in that console.
 */
public class InvalidProviderTokenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "invalid_firebase_token";

    public InvalidProviderTokenException() {
        super(CODE, "This sign-in could not be verified.", Map.of());
    }
}
