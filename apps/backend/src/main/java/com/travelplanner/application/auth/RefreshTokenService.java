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
 *
 * <p><b>Two refreshes at once count as reuse too.</b> The rotation itself is a conditional
 * {@code UPDATE} ({@link RefreshTokenPort#markRotated}), so of two simultaneous presentations of the
 * same token exactly one can succeed. The loser is treated identically to a sequential replay:
 * refusing to distinguish them is deliberate, because the only difference between "my client fired
 * twice" and "the thief refreshed a millisecond before me" is timing, and timing is precisely what
 * an attacker controls. The alternative — a grace window in which a second use is forgiven — is a
 * window in which theft is undetectable by design.
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
     * <p>The read is for <em>diagnosis</em>, not for the decision. Whether this caller may rotate is
     * settled by {@link RefreshTokenPort#markRotated}'s row count, because any precondition checked
     * in Java and acted on afterwards leaves a gap, and a concurrent refresh fits in the gap. What
     * the read is still needed for is telling an ordinary dead session apart from a replay: an
     * expired or logged-out token must not sign the account out of every other device.
     *
     * @return the owning user id, for which the caller mints a new access token
     * @throws UnauthorizedException for a missing, unknown, expired, revoked, or replayed token —
     *         uniformly, because a caller holding a bad refresh token learns nothing useful from
     *         being told which kind of bad it was
     */
    @TransactionalWrite
    public UUID rotate(String rawToken) {
        RefreshToken presented = require(rawToken);

        if (!presented.isRotated() && refreshTokens.markRotated(presented.tokenHash(), Instant.now())) {
            return presented.userId();
        }
        if (wasReplayed(presented)) {
            log.warn("Refresh token reuse detected for user {} — revoking the family",
                    presented.userId());
            revocation.revokeAllSessions(presented.userId(),
                    SessionRevocationReason.REFRESH_TOKEN_REUSE);
        }
        throw new UnauthorizedException();
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

    /**
     * Was the failed claim a replay, or just a dead session?
     *
     * <p>Only {@code rotated_at} distinguishes them, and it may have been set by the transaction
     * that won the race a moment ago — after this request's first read. Hence the second read, which
     * happens on the failure path only: the winning {@code UPDATE} had to commit before ours could
     * re-evaluate its predicate and report zero rows, so by the time we get here the successor is
     * visible. An expired or explicitly revoked token answers {@code false} and gets a plain 401,
     * because a fortnight-old tab and a signed-out one are not incidents.
     */
    private boolean wasReplayed(RefreshToken presented) {
        return presented.isRotated()
                || refreshTokens.findByTokenHash(presented.tokenHash())
                        .filter(RefreshToken::isRotated)
                        .isPresent();
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
