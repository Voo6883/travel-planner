package com.travelplanner.api.dto.auth;

import com.travelplanner.application.auth.LoginCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/login} — UC-A04. Field names are PLAN §4.0.5's
 * {@code LocalLoginRequest} verbatim.
 *
 * <p>Only presence and length are validated. Anything stricter — an email format check, a password
 * policy — would reject some inputs before the credential comparison and accept others, and the
 * difference in response is an oracle for what a valid identifier looks like. A malformed login is
 * simply a login that fails.
 */
public record LoginRequest(

        /** Email or username; the server decides which from the {@code '@'} (ADR 009 §4). */
        @NotBlank
        @Size(max = 320)
        String login,

        @NotBlank
        @Size(max = 200)
        String password) {

    public LoginCommand toCommand() {
        return new LoginCommand(login, password);
    }
}
