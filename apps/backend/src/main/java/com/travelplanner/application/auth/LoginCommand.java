package com.travelplanner.application.auth;

import com.travelplanner.domain.valueobject.ProviderCredential;
import java.util.Objects;

/**
 * A local sign-in attempt (UC-A04).
 *
 * <p>{@code identifier} is an email <em>or</em> a username — PLAN §4.0.5 accepts either on one
 * field. Which one it is can be decided from the string alone, because ADR 009 §4 forbids
 * {@code '@'} in a username; without that rule the two lookups could resolve to different accounts
 * for the same input.
 */
public record LoginCommand(String identifier, String password) {

    public LoginCommand {
        Objects.requireNonNull(password, "password");
        identifier = identifier == null ? "" : identifier.trim();
    }

    public ProviderCredential toCredential() {
        return new ProviderCredential(identifier, password);
    }

    /** @see com.travelplanner.domain.valueobject.ProviderCredential#toString() */
    @Override
    public String toString() {
        return "LoginCommand[identifier=" + identifier + ", password=***]";
    }
}
