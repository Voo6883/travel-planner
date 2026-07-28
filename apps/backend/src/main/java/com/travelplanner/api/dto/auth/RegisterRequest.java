package com.travelplanner.api.dto.auth;

import com.travelplanner.application.auth.RegisterCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/register} — UC-A01.
 *
 * <p>The password is checked by {@code PasswordPolicy}, not by a {@code @Size} here, so that
 * registration, change-password (task 09), and admin reset (task 12) cannot drift apart. Only the
 * shape rules that are specific to this request live on the DTO.
 *
 * <p>The username pattern excludes {@code '@'} by construction. ADR 009 §4 forbids it because
 * login accepts an email <em>or</em> a username on one field, and a username shaped like somebody
 * else's address would make that field ambiguous.
 */
public record RegisterRequest(

        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "may contain letters, digits, dot, underscore, and hyphen only")
        String username,

        @NotBlank
        String password) {

    public RegisterCommand toCommand() {
        return new RegisterCommand(email, username, password);
    }
}
