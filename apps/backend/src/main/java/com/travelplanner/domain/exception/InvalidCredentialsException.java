package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * Sign-in failed. Maps to {@code 401 invalid_credentials}.
 *
 * <p><strong>One exception for every cause.</strong> No such email, no such username, wrong
 * password, an OAuth-only account with no local password — all of them raise this, with the same
 * message and no {@code details}. Any distinction here becomes an account-existence oracle: an
 * attacker who can tell "no such user" from "wrong password" can enumerate the whole user base
 * with a password they know is wrong (ADR 009 §6, task 08 "Do not").
 *
 * <p>The two errors that <em>are</em> distinguishable —
 * {@link EmailNotVerifiedException} and {@link AccountDisabledException} — are only ever raised
 * after the password has already been verified, so they tell a caller nothing they did not
 * already know.
 */
public class InvalidCredentialsException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "invalid_credentials";

    public InvalidCredentialsException() {
        super(CODE, "The email, username, or password is incorrect.", Map.of());
    }
}
