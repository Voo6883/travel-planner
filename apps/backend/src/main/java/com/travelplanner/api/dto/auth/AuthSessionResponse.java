package com.travelplanner.api.dto.auth;

import com.travelplanner.application.auth.CurrentUserView;
import com.travelplanner.application.auth.ExternalSignIn;

/**
 * The reply to a successful {@code /auth/login}, {@code /auth/refresh}, or {@code /auth/firebase}.
 *
 * <p>The tokens are not in it. They travel as {@code Set-Cookie} headers, because a token in a JSON
 * body is a token JavaScript can read — which is precisely what ADR 002 rejected the
 * {@code Authorization} header for.
 *
 * <p>A wrapper around {@code user} rather than the user object itself, which is what let task 10
 * add PLAN §4.0.5's {@code is_new_user} and {@code provider_linked} for the Firebase and GitHub
 * paths without reshaping a response the local flow already published.
 *
 * <p>Both flags are always present and always {@code false} for the local paths. Omitting them
 * there would make the field's absence meaningful, and a client would have to distinguish "not
 * applicable" from "false" for no gain.
 */
public record AuthSessionResponse(
        CurrentUserResponse user, boolean isNewUser, boolean providerLinked) {

    /** Local sign-in and refresh: neither ever creates an account or links a provider. */
    public static AuthSessionResponse of(CurrentUserView view) {
        return new AuthSessionResponse(CurrentUserResponse.from(view), false, false);
    }

    /** UC-A02, UC-A03, UC-A05, UC-A06, UC-A09 — the flags come from the linking decision. */
    public static AuthSessionResponse of(CurrentUserView view, ExternalSignIn signIn) {
        return new AuthSessionResponse(
                CurrentUserResponse.from(view), signIn.newUser(), signIn.providerLinked());
    }
}
