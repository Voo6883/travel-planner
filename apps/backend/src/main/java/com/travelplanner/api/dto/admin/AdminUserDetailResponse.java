package com.travelplanner.api.dto.admin;

import com.travelplanner.application.admin.AdminUserView;
import com.travelplanner.domain.model.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The body of {@code GET /admin/users/{userId}} and of a successful {@code PUT} on it (UC-A15).
 *
 * <p>Flattened rather than composed from {@link AdminUserSummaryResponse}: the contract publishes
 * this as an {@code allOf} of the summary, and Jackson's inheritance-free record model has no way to
 * spread one record into another's JSON without either a wrapper object on the wire — which the
 * contract does not describe — or {@code @JsonUnwrapped}, which openapi-typescript cannot see. The
 * duplication is eight components and is checked by the contract test.
 *
 * @param hasLocalPassword {@code password_hash IS NOT NULL}. The single bit an administrator needs
 *        in order to know whether "reset password" applies (ADR 009 §4); the hash itself is never
 *        exposed in any form
 */
public record AdminUserDetailResponse(
        UUID userId,
        String email,
        String username,
        List<String> roles,
        boolean emailVerified,
        boolean enabled,
        boolean closed,
        Instant createdAt,
        List<String> linkedProviders,
        boolean hasLocalPassword,
        Instant updatedAt) {

    public static AdminUserDetailResponse from(AdminUserView view) {
        User account = view.account();
        return new AdminUserDetailResponse(account.id(), account.email(), account.username(),
                List.of(account.role().name()), account.emailVerified(), account.enabled(),
                account.isDeleted(), account.createdAt(), view.linkedProviders(),
                view.hasLocalPassword(), account.updatedAt());
    }
}
