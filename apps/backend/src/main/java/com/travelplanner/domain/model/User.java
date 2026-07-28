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
 * <p>The {@code withX} methods are the one exception, and they are not behaviour: they are the
 * copy-with-one-field-changed that a 12-component record otherwise forces every caller to write by
 * hand, listing all twelve and getting one of them wrong eventually. {@link #anonymised} is the
 * same idea with a rule attached — see UC-A14.
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
 * @param deletedAt UC-A14 — when the account was closed, or {@code null} while it is live. The row
 *        is retained because every trip references it; the identifying columns are anonymised
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
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * RFC 2606 reserves {@code .invalid} as a top-level domain that can never be registered, so an
     * anonymised address is guaranteed undeliverable and can never collide with a real one.
     */
    private static final String ANONYMISED_EMAIL_DOMAIN = "@deleted.invalid";

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
        if (deletedAt != null && passwordHash != null) {
            // Mirrors ck_user_deleted_has_no_password (V10). A closed account with a live
            // credential is the one state that would make the soft delete meaningless.
            throw ValidationFailedException.field("password_hash",
                    "must be absent on a deleted account");
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

    /** UC-A14 — the account was closed. Its row survives only so trips keep an owner. */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** UC-A08 — the verification link was followed. */
    public User withEmailVerified(boolean verified, Instant updatedAt) {
        return new User(id, username, email, passwordHash, verified, role, enabled, tokenVersion,
                sessionsValidAfter, deletedAt, createdAt, updatedAt);
    }

    /**
     * PLAN §4.0.6 — an administrator switched the account on or off.
     *
     * <p>The flag alone is not the feature. {@code JwtAuthenticationFilter} reads it on every
     * request, so a disabled account cannot make a <em>new</em> request — but tokens already issued
     * keep their {@code tv}, and the caller must therefore <em>also</em> terminate sessions through
     * {@code SessionRevocationService} (ADR 009 §1). This record cannot do that itself: the bump
     * has to be an atomic increment in SQL.
     */
    public User withEnabled(boolean nowEnabled, Instant updatedAt) {
        return new User(id, username, email, passwordHash, emailVerified, role, nowEnabled,
                tokenVersion, sessionsValidAfter, deletedAt, createdAt, updatedAt);
    }

    /** UC-A07 and UC-A12 — a reset or a self-service change. The caller supplies an encoded hash. */
    public User withPasswordHash(String newPasswordHash, Instant updatedAt) {
        return new User(id, username, email, newPasswordHash, emailVerified, role, enabled,
                tokenVersion, sessionsValidAfter, deletedAt, createdAt, updatedAt);
    }

    /**
     * UC-A14 — soft delete with PII anonymisation.
     *
     * <p>Four things happen together, and each one is load-bearing:
     *
     * <ul>
     *   <li>the email becomes {@code deleted-<id>@deleted.invalid} — unroutable, unique, and still
     *       satisfying the not-null column and the case-insensitive unique index, so the row stays
     *       valid without holding a real address;
     *   <li>the username is released, so the name the person chose becomes available again and no
     *       longer identifies them;
     *   <li>the password hash is dropped, so no credential survives the deletion — V10 makes that a
     *       CHECK constraint rather than a convention;
     *   <li>{@code enabled} is cleared, which is what {@code JwtAuthenticationFilter} reads on every
     *       request. The caller must <em>also</em> bump {@code tokenVersion} through
     *       {@code SessionRevocationService}; this record cannot do that itself, because the bump
     *       has to be an atomic increment in SQL (ADR 009 §1).
     * </ul>
     *
     * <p>{@code id}, {@code role}, and {@code createdAt} are kept deliberately. They carry no
     * personal information and they are what every referencing row and every audit trail joins on.
     *
     * <p>Static rather than an instance method, and not for style: MapStruct treats any single-
     * argument method that returns the declaring type as a fluent setter, so an instance
     * {@code anonymised(Instant)} makes {@code UserPersistenceMapper} fail the build with
     * "unmapped target property". The {@code withX} copiers above take two arguments and so are
     * invisible to that heuristic.
     */
    public static User anonymised(User account, Instant deletedAt) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(deletedAt, "deletedAt");
        return new User(account.id, null, anonymisedEmailFor(account.id), null, false,
                account.role, false, account.tokenVersion, account.sessionsValidAfter, deletedAt,
                account.createdAt, deletedAt);
    }

    private static String anonymisedEmailFor(UUID id) {
        return "deleted-" + id + ANONYMISED_EMAIL_DOMAIN;
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
