package com.travelplanner.domain.valueobject;

import java.util.Objects;
import java.util.Optional;

/**
 * What a caller presents to an identity provider, in the one shape every provider can accept
 * (PLAN §4.0.5).
 *
 * <p>Two providers need two different things, and a single record carries both without the port
 * growing a method per vendor:
 *
 * <ul>
 *   <li>{@code LOCAL} — {@code identifier} is the email or username, {@code secret} the password.
 *   <li>{@code FIREBASE_GOOGLE} / {@code GITHUB} (task 10) — {@code identifier} is absent and
 *       {@code secret} is the provider-issued token.
 * </ul>
 *
 * <p>{@code secret} is never logged and never persisted; it exists for the length of one
 * verification call.
 */
public record ProviderCredential(String identifier, String secret) {

    public ProviderCredential {
        Objects.requireNonNull(secret, "secret");
        identifier = identifier == null || identifier.isBlank() ? null : identifier.trim();
    }

    public Optional<String> identifierIfPresent() {
        return Optional.ofNullable(identifier);
    }

    /**
     * Deliberately overridden. The default record {@code toString()} prints every component, so a
     * single {@code log.debug("credential {}", credential)} anywhere would put a plaintext password
     * in the log file (PLAN §4.0.2-J2 forbids PII in logs; a password is worse than PII).
     */
    @Override
    public String toString() {
        return "ProviderCredential[identifier=" + identifier + ", secret=***]";
    }
}
