package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * Unlinking this provider would leave the account with no way to sign in. Maps to
 * {@code 409 last_sign_in_method} (ADR 009 §4).
 *
 * <p>An account created through Google has no local password, so its Google identity is the whole
 * of its credential set. Removing it would not "unlink a provider" — it would strand the account
 * and everything it owns, irreversibly and with no error to explain it later.
 *
 * <p>A local password counts as a method only when one is actually set: {@code password_hash IS
 * NULL} is exactly how ADR 009 §4 identifies an OAuth-only account elsewhere, and counting the
 * bookkeeping {@code LOCAL} identity row instead would let a passwordless account unlink its last
 * real credential.
 */
public class LastSignInMethodException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "last_sign_in_method";

    public LastSignInMethodException() {
        super(CODE, "This is the only way to sign in to this account.", Map.of());
    }
}
