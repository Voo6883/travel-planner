package com.travelplanner.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/auth/password/reset} — UC-A07, step two.
 *
 * <p>{@code token} carries no constraint, for the reason
 * {@link ConfirmEmailVerificationRequest} explains: every unusable token must produce one
 * {@code invalid_token}, not a split between that and {@code validation_failed}.
 *
 * <p>{@code newPassword} carries only {@code @NotBlank}. The length and strength rules live in
 * {@code PasswordPolicy}, so registration, reset, change, and task 12's admin reset cannot drift
 * apart — a {@code @Size} written on one DTO is a rule that quietly does not exist on the others.
 *
 * <p>Order matters at the service: the password is checked against the policy <em>before</em> the
 * token is spent, so a typo does not burn the one link the user has.
 */
public record ResetPasswordRequest(

        String token,

        @NotBlank
        String newPassword) {
}
