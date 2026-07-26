package com.travelplanner.domain.valueobject;

import com.travelplanner.domain.enums.Role;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Who is calling. The second parameter of every service use-case (PLAN §4.0.2-B2) and the source
 * of the {@code user_id} that scopes every query (PLAN §4.0.2-L).
 *
 * <p>Shape follows PLAN §4.0.5 exactly — {@code { userId, email, roles[] }}. The plan writes it
 * with a Jakarta {@code @NotNull}; that annotation is dropped here because a domain type may not
 * import a framework (PLAN §4.0.2-F), and the same guarantee is enforced by the compact
 * constructor instead. See the task report for this conflict.
 *
 * <p>{@code roles} stays {@code List<String>} to match the plan's declaration and the
 * {@code /auth/me} payload. {@link #hasRole(Role)} is the type-safe way to read it, so call sites
 * compare against the {@link Role} enum rather than repeating string literals.
 *
 * <p>Constructed per request by task 08 from current database state, never from token claims alone
 * — ADR 009 §2 makes user state authoritative on every request so that revocation and
 * {@code email_verified} cannot go stale inside a live token.
 */
public record UserContext(UUID userId, String email, List<String> roles) {

    public UserContext {
        Objects.requireNonNull(userId, "userId");
        // Defensive copy: this record is passed into services that must not be able to mutate the
        // caller's authority list.
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /** Convenience for the ordinary single-role case. */
    public static UserContext of(UUID userId, String email, Role role) {
        return new UserContext(userId, email, List.of(role.name()));
    }

    public boolean hasRole(Role role) {
        return role != null && roles.contains(role.name());
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
