package com.travelplanner.api.dto.auth;

import com.travelplanner.application.auth.CurrentUserView;

/**
 * The reply to a successful {@code /auth/login} or {@code /auth/refresh}.
 *
 * <p>The tokens are not in it. They travel as {@code Set-Cookie} headers, because a token in a JSON
 * body is a token JavaScript can read — which is precisely what ADR 002 rejected the
 * {@code Authorization} header for.
 *
 * <p>A wrapper around {@code user} rather than the user object itself, so that task 10 can add
 * PLAN §4.0.5's {@code is_new_user} and {@code provider_linked} for the Firebase and GitHub paths
 * without reshaping a response the local flow already publishes.
 */
public record AuthSessionResponse(CurrentUserResponse user) {

    public static AuthSessionResponse of(CurrentUserView view) {
        return new AuthSessionResponse(CurrentUserResponse.from(view));
    }
}
