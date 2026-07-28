package com.travelplanner.application.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Mints and verifies the self-issued access token (ADR 002, PLAN §4.0.5 names this file).
 *
 * <p>An interface here and the signing implementation in {@code infrastructure/auth/jwt/}: the
 * JOSE library, the algorithm, and the key material are all replaceable security decisions, and no
 * service should have to change when they are. It also means the authentication paths can be unit
 * tested without a real key.
 */
public interface JwtTokenService {

    /**
     * @param tokenVersion the account's current {@code token_version}, frozen into the token so a
     *        later bump invalidates it
     * @return a signed, compact JWT
     */
    String issue(UUID userId, int tokenVersion);

    /**
     * Verifies the signature and the expiry.
     *
     * <p>Returns {@link Optional#empty()} rather than throwing, for every failure: an expired
     * token, a tampered payload, a token signed with another key, and outright garbage are all the
     * same thing to a caller — this request has no session. Exceptions thrown inside the security
     * filter would not reach {@code GlobalExceptionHandler} anyway.
     */
    Optional<AccessTokenClaims> parse(String token);
}
