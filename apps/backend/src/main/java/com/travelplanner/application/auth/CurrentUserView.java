package com.travelplanner.application.auth;

import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import java.util.List;
import java.util.UUID;

/**
 * The profile behind {@code GET /auth/me} (UC-A11) and the body of every successful sign-in.
 *
 * <p>Assembled from current database state on each call, never from token claims — the same
 * ADR 009 §2 rule that governs {@code UserContext}. {@code emailVerified} in particular has to be
 * live: the UI shows a "verify your email" banner from it, and a value frozen at login would keep
 * the banner up for half an hour after the user clicked the link.
 *
 * <p>{@code linkedProviders} answers UC-A11's "view linked providers". In task 08 it is always
 * {@code [LOCAL]}; task 10 adds {@code FIREBASE_GOOGLE} and {@code GITHUB} rows without this shape
 * changing, which is what ADR 002 means by "OAuth addition does not change {@code UserContext}".
 *
 * <p>No password hash, no token version, no lockout state. This record is serialised straight to
 * the wire, so anything added to it is published.
 */
public record CurrentUserView(
        UUID userId,
        String email,
        String username,
        List<String> roles,
        boolean emailVerified,
        List<String> linkedProviders) {

    public CurrentUserView {
        roles = roles == null ? List.of() : List.copyOf(roles);
        linkedProviders = linkedProviders == null ? List.of() : List.copyOf(linkedProviders);
    }

    public static CurrentUserView of(User user, List<UserIdentity> identities) {
        return new CurrentUserView(
                user.id(),
                user.email(),
                user.username(),
                List.of(user.role().name()),
                user.emailVerified(),
                identities.stream().map(UserIdentity::provider).map(AuthProvider::name).sorted().toList());
    }
}
