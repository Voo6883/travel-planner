package com.travelplanner.application.auth;

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.AuthSecurityProperties;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.UnauthorizedException;
import com.travelplanner.domain.model.RefreshToken;
import com.travelplanner.domain.port.RefreshTokenPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues, rotates, and revokes refresh tokens (ADR 009 §3).
 *
 * <p>Three properties define the design:
 *
 * <ul>
 *   <li><b>Opaque and random.</b> A refresh token carries no claims — it is 256 bits from
 *       {@link SecureRandom}. There is nothing in it to forge, and nothing in it to read.
 *   <li><b>Stored hashed.</b> Only the SHA-256 digest reaches the database, so a stolen dump
 *       yields no usable 14-day sessions. SHA-256 rather than BCrypt on purpose: the input is
 *       already full-entropy random, so there is no dictionary to slow down, and the lookup has to
 *       be an indexed equality match rather than a scan-and-compare over every row.
 *   <li><b>Rotated on every use.</b> The presented token is marked {@code rotated_at} and a new
 *       one is issued. This is what makes theft detectable at all.
 * </ul>
 *
 * <p><b>Reuse detection.</b> If a token that was already rotated is presented again, exactly one
 * of two things happened: an attacker stole it and the legitimate client has since refreshed, or
 * the attacker refreshed first and the legitimate client is now replaying. There is no way to tell
 * which party is which, so the only safe response is to distrust both — the whole family is
 * revoked through {@link SessionRevocationService}, which also bumps {@code token_version} and
 * kills the access tokens the thief already holds.
 */
@Service
@RequiresDatabase
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 256 bits, base64url-encoded. Comfortably beyond guessing, and cookie-safe without escaping. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenPort refreshTokens;
    private final SessionRevocationService revocation;
    private final AuthSecurityProperties.Session properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenPort refreshTokens, SessionRevocationService revocation,
            AuthSecurityProperties properties) {
        this.refreshTokens = refreshTokens;
        this.revocation = revocation;
        this.properties = properties.getSession();
    }

    /** @return the raw token, which exists only here and in the cookie — never in the database */
    @TransactionalWrite
    public String issue(UUID userId) {
        String raw = randomToken();
        Instant expiresAt = Instant.now().plus(properties.getRefreshTokenTtl());
        refreshTokens.save(RefreshToken.issued(userId, hash(raw), expiresAt));
        return raw;
    }

    /**
     * Validates and consumes a presented refresh token, rotating it.
     *
     * @return the owning user id, for which the caller mints a new access token
     * @throws UnauthorizedException for a missing, unknown, expired, revoked, or replayed token —
     *         uniformly, because a caller holding a bad refresh token learns nothing useful from
     *         being told which kind of bad it was
     */
    @TransactionalWrite
    public UUID rotate(String rawToken) {
        RefreshToken presented = require(rawToken);
        Instant now = Instant.now();

        if (presented.isRotated()) {
            // Replay of a superseded token. Distrust every session for this account.
            log.warn("Refresh token reuse detected for user {} — revoking the family",
                    presented.userId());
            revocation.revokeAllSessions(presented.userId(), SessionRevocationReason.REFRESH_TOKEN_REUSE);
            throw new UnauthorizedException();
        }
        if (!presented.isUsableAt(now)) {
            throw new UnauthorizedException();
        }

        refreshTokens.save(presented.rotatedAt(now));
        return presented.userId();
    }

    /**
     * Invalidates one token server-side, for logout. Silent when the token is absent or already
     * unusable — logout is idempotent and must never fail a caller who is trying to sign out.
     */
    @TransactionalWrite
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawToken))
                .filter(token -> token.revokedAt() == null)
                .ifPresent(token -> refreshTokens.save(token.revokedAt(Instant.now())));
    }

    /**
     * The session layer's single door to {@link SessionRevocationService}. Everything that has to
     * sign an account out everywhere goes through here, so no caller has to remember that
     * revocation is three writes rather than one.
     */
    public void revokeAllSessions(UUID userId, SessionRevocationReason reason) {
        revocation.revokeAllSessions(userId, reason);
    }

    private RefreshToken require(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException();
        }
        return refreshTokens.findByTokenHash(hash(rawToken)).orElseThrow(UnauthorizedException::new);
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex, 64 characters — the width {@code refresh_token.token_hash} declares. */
    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // Every JVM is required to ship SHA-256; if it is missing the platform is broken.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
