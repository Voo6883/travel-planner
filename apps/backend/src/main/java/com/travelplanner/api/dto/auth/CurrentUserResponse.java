package com.travelplanner.api.dto.auth;

import com.travelplanner.application.auth.CurrentUserView;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated caller — the body of {@code GET /auth/me} (UC-A11) and of every successful
 * sign-in.
 *
 * <p>Serialised snake_case by the global Jackson strategy, so the wire fields are {@code user_id},
 * {@code email_verified}, and {@code linked_providers}.
 *
 * <p>This is what {@code useUserContext()} on the frontend is built from (ADR 002). It carries no
 * token: the session is an httpOnly cookie the page cannot read, by design.
 *
 * @param emailVerified drives the UC-A08 gate and the "verify your email" banner. Read from the
 *        database on every call, never from a claim (ADR 009 §2).
 * @param linkedProviders {@code [LOCAL]} in task 08; task 10 adds {@code FIREBASE_GOOGLE} and
 *        {@code GITHUB} without changing this shape.
 */
public record CurrentUserResponse(
        UUID userId,
        String email,
        String username,
        List<String> roles,
        boolean emailVerified,
        List<String> linkedProviders) {

    public static CurrentUserResponse from(CurrentUserView view) {
        return new CurrentUserResponse(view.userId(), view.email(), view.username(), view.roles(),
                view.emailVerified(), view.linkedProviders());
    }
}
