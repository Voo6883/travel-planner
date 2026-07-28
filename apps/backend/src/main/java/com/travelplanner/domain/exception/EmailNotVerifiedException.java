package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The account exists and the password was correct, but the address has not been confirmed. Maps to
 * {@code 403 email_not_verified}.
 *
 * <p>UC-A08: a local sign-up cannot reach the planner until {@code email_verified=true}, and the
 * login error state is where the user asks for another verification mail. That resend affordance
 * is why the failure is a distinct code rather than folded into
 * {@link InvalidCredentialsException} — the frontend cannot offer "resend" for an error it cannot
 * tell apart.
 *
 * <p>Raised only <em>after</em> the password check has passed, so it leaks nothing to anyone who
 * does not already hold the credential.
 */
public class EmailNotVerifiedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "email_not_verified";

    public EmailNotVerifiedException() {
        super(CODE, "This email address has not been verified yet.", Map.of());
    }
}
