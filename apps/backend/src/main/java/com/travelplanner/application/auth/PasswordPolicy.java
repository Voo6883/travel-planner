package com.travelplanner.application.auth;

import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.port.PasswordHasherPort;
import org.springframework.stereotype.Component;

/**
 * The one place a password is judged acceptable and turned into a hash (PLAN §4.0.9: BCrypt
 * strength 10+, minimum length 8).
 *
 * <p>Validation and hashing live together on purpose: {@link #encode(String)} cannot be called
 * without the rule having run, so there is no path that stores a hash of a password nobody
 * checked. A Bean Validation annotation on the registration DTO would not have that property —
 * task 09's change-password and admin reset are different DTOs, and a rule written on one is a
 * rule that quietly does not exist on the others.
 *
 * <p>Failures are {@code validation_failed} against the wire field name so the frontend can
 * highlight the field. The message states the rule and never echoes the input.
 */
@Component
public class PasswordPolicy {

    static final String FIELD = "password";

    private final PasswordHasherPort hasher;
    private final AuthSecurityProperties.Password properties;

    public PasswordPolicy(PasswordHasherPort hasher, AuthSecurityProperties properties) {
        this.hasher = hasher;
        this.properties = properties.getPassword();
    }

    /** @throws ValidationFailedException if the password does not satisfy the policy */
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw ValidationFailedException.field(FIELD, "must not be blank");
        }
        if (rawPassword.length() < properties.getMinLength()) {
            throw ValidationFailedException.field(FIELD,
                    "must be at least " + properties.getMinLength() + " characters");
        }
        if (rawPassword.length() > properties.getMaxLength()) {
            // Not an arbitrary ceiling: BCrypt hashes the first 72 bytes and ignores the rest, so
            // accepting a longer value would authenticate a different password than the user chose.
            throw ValidationFailedException.field(FIELD,
                    "must be at most " + properties.getMaxLength() + " characters");
        }
    }

    /** Validates, then hashes. Never returns a hash of an unvalidated password. */
    public String encode(String rawPassword) {
        validate(rawPassword);
        return hasher.hash(rawPassword);
    }

    /**
     * Constant-time check of a candidate against a stored hash.
     *
     * <p>Here rather than in each caller so that "how a password is judged" stays one class. Task
     * 09's change-password needs to verify the current password, and injecting
     * {@link PasswordHasherPort} into an application service to do it would start a second path to
     * the algorithm — the thing this class exists to prevent.
     *
     * <p>No validation runs first, deliberately: an existing password predates any policy change,
     * and rejecting it for being too short would lock its owner out of the very endpoint that lets
     * them fix it.
     *
     * @param storedHash may be {@code null} for an OAuth-only account (ADR 009 §4); the
     *        implementation still consumes comparable time, so timing reveals nothing
     */
    public boolean matches(String rawPassword, String storedHash) {
        return hasher.matches(rawPassword, storedHash);
    }
}
