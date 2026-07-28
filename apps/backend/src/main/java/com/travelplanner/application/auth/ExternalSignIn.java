package com.travelplanner.application.auth;

import java.util.Objects;

/**
 * The result of signing in through Firebase or GitHub: the same session every other path issues,
 * plus the two flags PLAN §4.0.5 publishes on {@code AuthResponse}.
 *
 * <p>The session is an {@link IssuedSession} and nothing provider-specific — no Firebase ID token,
 * no GitHub access token, no provider refresh token. Task 10's "do not issue Firebase/GitHub tokens
 * as the application session" is a type-level fact here rather than a review comment: there is
 * nowhere in this record to put one.
 */
public record ExternalSignIn(IssuedSession session, boolean newUser, boolean providerLinked) {

    public ExternalSignIn {
        Objects.requireNonNull(session, "session");
    }

    public static ExternalSignIn of(IssuedSession session, LinkedAccount account) {
        return new ExternalSignIn(session, account.newUser(), account.providerLinked());
    }
}
