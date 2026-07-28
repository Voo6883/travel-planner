package com.travelplanner.api.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code PUT /api/v1/admin/users/{userId}/reset-password} — UC-A16.
 *
 * <p>No {@code @Size}: {@code PasswordPolicy} owns the length and strength rules for every flow that
 * sets a password, exactly as it does for {@code ChangePasswordRequest}. Restating them here would
 * create a second definition that drifts.
 *
 * <p>{@code toString} is overridden because a record prints every component by default, and a
 * request object is precisely the kind of thing that ends up inside a binding-failure message or a
 * debug log. The value is never returned, never mailed, and never logged.
 */
public record AdminResetPasswordRequest(@NotBlank String newPassword) {

    @Override
    public String toString() {
        return "AdminResetPasswordRequest[newPassword=***]";
    }
}
