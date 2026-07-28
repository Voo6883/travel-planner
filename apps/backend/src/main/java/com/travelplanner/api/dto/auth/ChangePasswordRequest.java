package com.travelplanner.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/auth/password} — UC-A12.
 *
 * <p>There is no user id here. The account comes from the session cookie, because a body-supplied
 * id would be a caller asserting whose password they are changing.
 *
 * <p>{@code currentPassword} is validated for presence and an upper bound only, exactly as
 * {@link LoginRequest#password()} is: anything stricter would reject some candidate passwords
 * before the comparison and accept others, and the difference in response is an oracle for what a
 * valid password looks like. A wrong current password is simply a wrong password.
 *
 * <p>{@code newPassword} gets no {@code @Size} — {@code PasswordPolicy} owns the length and
 * strength rules for every flow that sets a password.
 */
public record ChangePasswordRequest(

        @NotBlank
        @Size(max = 200)
        String currentPassword,

        @NotBlank
        String newPassword) {
}
