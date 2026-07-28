package com.travelplanner.api.dto.admin;

import com.travelplanner.domain.model.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of {@code GET /admin/users} (UC-A15). Serialised snake_case by the global Jackson
 * strategy, so the wire fields are {@code user_id}, {@code email_verified}, {@code created_at}.
 *
 * <p><strong>Account state only.</strong> No password hash, no token version, no session data, and
 * nothing about trips, chats, or bookings — PLAN §4.0.6 confines administration to accounts, and
 * this record is serialised straight to the wire, so a field added here is a field published.
 *
 * @param closed the owner deleted the account (UC-A14). Distinct from {@code enabled}, which a
 *        closed account also has false: without both flags an administrator cannot tell an account
 *        they switched off from one its owner closed, and only the first can be switched back on
 */
public record AdminUserSummaryResponse(
        UUID userId,
        String email,
        String username,
        List<String> roles,
        boolean emailVerified,
        boolean enabled,
        boolean closed,
        Instant createdAt) {

    public static AdminUserSummaryResponse from(User account) {
        return new AdminUserSummaryResponse(account.id(), account.email(), account.username(),
                List.of(account.role().name()), account.emailVerified(), account.enabled(),
                account.isDeleted(), account.createdAt());
    }
}
