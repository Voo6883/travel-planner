package com.travelplanner.domain.model;

import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.ValidationFailedException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The account aggregate (PLAN §8).
 *
 * <p><strong>State only.</strong> Password hashing, lockout counters, verification tokens, and
 * session issuance belong to tasks 08 and 09. This type exists now because {@link Trip} is
 * user-scoped and cannot be persisted or tested without an owner.
 *
 * <p>{@code username} and {@code passwordHash} are both nullable, and their absence is meaningful
 * rather than accidental: an account created through Google or GitHub has neither. ADR 009 §4 keys
 * a security rule off exactly that — {@code passwordHash == null} means the forgot-password flow
 * must not mint a local password for an OAuth-only account.
 *
 * <p>No {@code version} column: ADR 008 §1 lists the aggregates that get one and {@code user} is
 * not among them. Concurrent edits to an account are administrative and rare, and the revocation
 * counter below is a different mechanism with a different purpose.
 *
 * @param tokenVersion ADR 009 §1 — bumped to revoke every live session
 * @param sessionsValidAfter ADR 009 §1 — tokens issued before this instant are rejected
 */
public record User(
        UUID id,
        String username,
        String email,
        String passwordHash,
        boolean emailVerified,
        Role role,
        boolean enabled,
        int tokenVersion,
        Instant sessionsValidAfter,
        Instant createdAt,
        Instant updatedAt) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        email = requireEmail(email);
        username = normaliseUsername(username);
        if (tokenVersion < 0) {
            throw ValidationFailedException.field("token_version", "must not be negative");
        }
    }

    public Optional<String> usernameIfPresent() {
        return Optional.ofNullable(username);
    }

    public Optional<String> passwordHashIfPresent() {
        return Optional.ofNullable(passwordHash);
    }

    /** ADR 009 §4: no local password means the forgot-password flow must not mint one. */
    public boolean isOAuthOnly() {
        return passwordHash == null;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    private static String requireEmail(String email) {
        String trimmed = email == null ? "" : email.trim();
        if (trimmed.isEmpty()) {
            throw ValidationFailedException.field("email", "must not be blank");
        }
        return trimmed;
    }

    private static String normaliseUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            // Empty string and "absent" must not both be representable: the unique index treats
            // them differently and two OAuth accounts would then collide on "".
            return null;
        }
        if (trimmed.indexOf('@') >= 0) {
            // ADR 009 §4. Login accepts email OR username; a username containing '@' can
            // impersonate somebody else's email address on that path.
            throw ValidationFailedException.field("username", "must not contain '@'");
        }
        return trimmed;
    }
}
