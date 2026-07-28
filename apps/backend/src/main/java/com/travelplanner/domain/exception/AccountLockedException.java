package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * Too many failed sign-in attempts for this identifier from this address. Maps to
 * {@code 423 account_locked} (ADR 009 §6: 5 failures / 15 minutes).
 *
 * <p>Carries {@code details.retry_after_seconds} so the UI can show a countdown instead of
 * inviting a sixth attempt that is guaranteed to fail.
 *
 * <p>This is <em>not</em> an existence oracle. The window counts attempts against identifiers that
 * were never registered too, so a locked response says only "this address has been guessing", not
 * "this account exists".
 */
public class AccountLockedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "account_locked";

    public AccountLockedException(long retryAfterSeconds) {
        super(CODE, "Too many failed sign-in attempts. Try again later.",
                Map.of("retry_after_seconds", Math.max(0L, retryAfterSeconds)));
    }
}
