package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The OAuth callback presented no {@code state}, or one that does not match the cookie issued at
 * start. Maps to {@code 400 invalid_oauth_state} (ADR 004 Security: "`state` param CSRF protection
 * on OAuth; validate callback").
 *
 * <p>Without this check the callback is an open endpoint that signs a browser into whatever account
 * an attacker's authorization code names — login CSRF, with the victim's subsequent activity
 * accruing in the attacker's account. Issuing a random {@code state} is only half of it; the half
 * that matters is refusing everything that does not match what was issued.
 *
 * <p>A missing cookie, an expired one, a missing parameter, and a mismatched value all raise this.
 * The distinction is of no use to a legitimate caller and of considerable use to anyone probing.
 */
public class InvalidOAuthStateException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "invalid_oauth_state";

    public InvalidOAuthStateException() {
        super(CODE, "This sign-in link is no longer valid. Please start again.", Map.of());
    }
}
