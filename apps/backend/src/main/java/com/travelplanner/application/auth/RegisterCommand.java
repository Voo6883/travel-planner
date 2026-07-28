package com.travelplanner.application.auth;

import java.util.Locale;
import java.util.Objects;

/**
 * A local sign-up request, normalised (UC-A01).
 *
 * <p>The email is lower-cased here rather than at the database boundary so that every later
 * comparison — the existence check, the unique index, the login lookup — sees the same string.
 * The username keeps its case for display but is compared case-insensitively, which is what
 * {@code ux_user_username_lower} enforces (ADR 009 §4).
 */
public record RegisterCommand(String email, String username, String password) {

    public RegisterCommand {
        Objects.requireNonNull(password, "password");
        email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        username = username == null ? null : username.trim();
    }

    /** @see com.travelplanner.domain.valueobject.ProviderCredential#toString() */
    @Override
    public String toString() {
        return "RegisterCommand[email=" + email + ", username=" + username + ", password=***]";
    }
}
