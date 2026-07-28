package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The credential was correct but the account is switched off ({@code user.enabled = false}). Maps
 * to {@code 403 account_disabled}.
 *
 * <p>An administrator disabling an account (PLAN §4.0.6, task 12) also bumps {@code token_version},
 * so live sessions stop working immediately rather than at the next token expiry (ADR 009 §1).
 * This exception is what the owner sees when they try to sign in again afterwards.
 *
 * <p>Raised only after the password has verified, for the same reason as
 * {@link EmailNotVerifiedException}.
 */
public class AccountDisabledException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "account_disabled";

    public AccountDisabledException() {
        super(CODE, "This account has been disabled.", Map.of());
    }
}
