package com.travelplanner.application.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The claims this system puts in — and reads back out of — its own access token (ADR 009 §1).
 *
 * <p><strong>What is deliberately absent matters more than what is here.</strong> There is no
 * email, no role, and no {@code email_verified}. Every one of those can change while a token is
 * still valid, and a token that carries them keeps asserting the old value for up to the full
 * 30-minute lifetime. ADR 009 §2 resolves this by reading user state on each request; the token
 * therefore carries only what identifies the session and what proves it has not been revoked.
 *
 * @param userId the {@code sub} claim
 * @param tokenVersion the {@code tv} claim — compared against {@code user.token_version}; a
 *        mismatch means the account revoked every session issued before the bump
 * @param issuedAt the {@code iat} claim — compared against {@code user.sessions_valid_after}
 */
public record AccessTokenClaims(UUID userId, int tokenVersion, Instant issuedAt) {

    public AccessTokenClaims {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(issuedAt, "issuedAt");
    }
}
