package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * A verification or password-reset link is unknown, expired, or already spent. Maps to
 * {@code 400 invalid_token}.
 *
 * <p><strong>One exception for all three causes</strong>, on the same reasoning as
 * {@link InvalidCredentialsException}. "Expired" tells the holder of a random string that the
 * string was once a real token issued to a real account; "unknown" tells them it never was. That
 * difference is a fact about somebody else's mailbox, and there is no version of the user
 * experience that needs it — the correct next step is identical in every case: ask for a fresh
 * link.
 *
 * <p>{@code 400} rather than {@code 401}: the caller is not failing to authenticate, they have
 * submitted a value that is no longer valid input. Returning {@code 401} would also invite the
 * frontend's session-expiry handling to fire on a page the user reached from an email, signed out.
 */
public class InvalidTokenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "invalid_token";

    public InvalidTokenException() {
        super(CODE, "This link is no longer valid.", Map.of());
    }
}
